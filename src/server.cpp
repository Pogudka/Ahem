#include <iostream>
#include <string>
#include <vector>
#include <map>
#include <fstream>
#include <sstream>
#include <cstring>
#include <cstdint>
#include <cerrno>
#include <thread>
#include <mutex>
#include <sys/socket.h>
#include <netinet/in.h>
#include <unistd.h>

const int CHAT_PORT = 9999;
const int FEED_PORT = 8080;

void sendAllBytes(int sock, const char* data, size_t len) {
    size_t off = 0;
    while (off < len) {
        ssize_t n = send(sock, data + off, len - off, 0);
        if (n <= 0) { if (n < 0 && (errno == EINTR || errno == EAGAIN)) continue; break; }
        off += (size_t)n;
    }
}
void sendAllBytes(int sock, const std::string& s) { sendAllBytes(sock, s.c_str(), s.size()); }
bool toInt(const std::string& s, int& out) { try { out = std::stoi(s); return true; } catch (...) { return false; } }

std::map<std::string, int> nameToSock;
std::mutex nameMtx;
std::vector<int> clients;
std::mutex clientsMtx;

void sendAll(const std::string& t) { std::lock_guard<std::mutex> lk(clientsMtx); for (int c : clients) sendAllBytes(c, t); }
void sendTo(const std::string& name, const std::string& t) { std::lock_guard<std::mutex> lk(nameMtx); auto it = nameToSock.find(name); if (it != nameToSock.end()) sendAllBytes(it->second, t); }
void dropFromClients(int s) { std::lock_guard<std::mutex> lk(clientsMtx); for (size_t i = 0; i < clients.size(); i++) if (clients[i] == s) { clients.erase(clients.begin() + i); return; } }
void handleChat(int sock) {
    std::string acc; char buf[8192]; std::string myName; bool reg = false;
    while (true) {
        int n = recv(sock, buf, sizeof(buf), 0);
        if (n <= 0) break;
        acc.append(buf, n);
        size_t p;
        while ((p = acc.find('\n')) != std::string::npos) {
            std::string line = acc.substr(0, p); acc.erase(0, p + 1);
            if (line.empty()) continue;
            if (!reg) {
                if (line.rfind("REG ", 0) == 0) {
                    myName = line.substr(4);
                    int old = -1;
                    { std::lock_guard<std::mutex> lk(nameMtx); auto it = nameToSock.find(myName);
                      if (it != nameToSock.end() && it->second != sock) { old = it->second; it->second = sock; } else nameToSock[myName] = sock; }
                    if (old != -1) { dropFromClients(old); shutdown(old, SHUT_RDWR); }
                    { std::lock_guard<std::mutex> lk(clientsMtx); clients.push_back(sock); }
                    reg = true; sendAll("=== " + myName + " в сети ===\n");
                }
                continue;
            }
            if (line[0] == '@') { size_t sp = line.find(' '); if (sp == std::string::npos) continue; sendTo(line.substr(1, sp - 1), line.substr(sp + 1) + "\n"); }
            else sendAll(line + "\n");
        }
    }
    bool act = false;
    { std::lock_guard<std::mutex> lk(nameMtx); auto it = nameToSock.find(myName);
      if (it != nameToSock.end() && it->second == sock) { nameToSock.erase(it); act = !myName.empty(); } }
    dropFromClients(sock);
    if (act) sendAll("=== " + myName + " вышел ===\n");
    close(sock);
}
void chatLoop() {
    int ss = socket(AF_INET, SOCK_STREAM, 0);
    int yes = 1; setsockopt(ss, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(yes));
    sockaddr_in a{}; a.sin_family = AF_INET; a.sin_addr.s_addr = INADDR_ANY; a.sin_port = htons(CHAT_PORT);
    if (bind(ss, (sockaddr*)&a, sizeof(a)) < 0) { std::cout << "[chat] порт занят\n"; return; }
    listen(ss, 5); std::cout << "[chat] жду на " << CHAT_PORT << "\n";
    while (true) { int c = accept(ss, nullptr, nullptr); if (c < 0) continue; std::thread(handleChat, c).detach(); }
}

const std::string DB_USERS = "users.dat";
const std::string DB_POSTS = "posts.dat";
const std::string DB_EDGES = "edges.dat";

struct User { int id; std::string email, nick, phash; };
struct Post { int id, author; std::string ts, text, img; };
struct Edge { int follower, target; };
struct Like { int post, user; };

std::vector<User> users; std::vector<Post> posts; std::vector<Edge> follows; std::vector<Like> likes;
int nextUid = 1, nextPid = 1;
std::mutex dbMtx;

uint64_t fnv1a(const std::string& s) { uint64_t h = 1469598103934665603ULL; for (unsigned char c : s) { h ^= c; h *= 1099511628211ULL; } return h; }
std::string demoHash(const std::string& pass, const std::string& email) { uint64_t h = fnv1a(pass + "@" + email); for (int i = 0; i < 10000; i++) h = fnv1a(std::to_string(h) + email); std::ostringstream o; o << h; return o.str(); }
std::vector<std::string> splitPipe(const std::string& s) { std::vector<std::string> v; std::stringstream ss(s); std::string t; while (std::getline(ss, t, '|')) v.push_back(t); return v; }
std::string tsNow() { std::time_t now = std::time(0); std::tm* t = std::localtime(&now); char b[6]; std::strftime(b, sizeof(b), "%H:%M", t); return std::string(b); }
void writeAtomic(const std::string& path, const std::string& content) { std::string tmp = path + ".tmp"; { std::ofstream f(tmp); f << content; } std::rename(tmp.c_str(), path.c_str()); }
void saveDB() {
    { std::ostringstream o; for (auto& u : users) o << u.id << "|" << u.email << "|" << u.nick << "|" << u.phash << "\n"; writeAtomic(DB_USERS, o.str()); }
    { std::ostringstream o; for (auto& p : posts) o << p.id << "|" << p.author << "|" << p.ts << "|" << p.text << "|" << p.img << "\n"; writeAtomic(DB_POSTS, o.str()); }
    { std::ostringstream o; for (auto& e : follows) o << "F|" << e.follower << "|" << e.target << "\n"; for (auto& l : likes) o << "L|" << l.post << "|" << l.user << "\n"; writeAtomic(DB_EDGES, o.str()); }
}
void loadDB() {
    { std::ifstream f(DB_USERS); std::string ln;
      while (std::getline(f, ln)) { auto v = splitPipe(ln); if (v.size() >= 4) { int id; if (!toInt(v[0], id)) continue;
          User u; u.id = id; u.email = v[1]; u.nick = v[2]; u.phash = v[3]; users.push_back(u); if (u.id >= nextUid) nextUid = u.id + 1; } } }
    { std::ifstream f(DB_POSTS); std::string ln;
      while (std::getline(f, ln)) { auto v = splitPipe(ln);
          if (v.size() >= 4) { int id, au; if (!toInt(v[0], id) || !toInt(v[1], au)) continue;
              Post p; p.id = id; p.author = au; p.ts = v[2]; p.text = v[3]; p.img = (v.size() >= 5) ? v[4] : "";
              posts.push_back(p); if (p.id >= nextPid) nextPid = p.id + 1; } } }
    { std::ifstream f(DB_EDGES); std::string ln;
      while (std::getline(f, ln)) { auto v = splitPipe(ln); if (v.size() >= 3) { int a, b; if (!toInt(v[1], a) || !toInt(v[2], b)) continue;
          if (v[0] == "F") follows.push_back({ a, b }); else if (v[0] == "L") likes.push_back({ a, b }); } } }
}
User* findUser(int id) { for (auto& u : users) if (u.id == id) return &u; return nullptr; }
User* findUserNick(const std::string& n) { for (auto& u : users) if (u.nick == n) return &u; return nullptr; }
User* findUserEmail(const std::string& e) { for (auto& u : users) if (u.email == e) return &u; return nullptr; }
std::string nickOf(int id) { User* u = findUser(id); return u ? u->nick : "?"; }
void sendStr(int sock, const std::string& s) { sendAllBytes(sock, s + "\n"); }

void feedCmd(int sock, const std::string& line) {
    std::lock_guard<std::mutex> lk(dbMtx);
    auto v = splitPipe(line); if (v.empty()) return;
    const std::string& cmd = v[0];
    if (cmd == "REG") {
        if (v.size() < 4) { sendStr(sock, "ERR|fields"); return; }
        if (findUserEmail(v[1])) { sendStr(sock, "ERR|exists"); return; }
        User u; u.id = nextUid++; u.email = v[1]; u.nick = v[2]; u.phash = demoHash(v[3], v[1]);
        users.push_back(u); saveDB(); sendStr(sock, "OK|" + std::to_string(u.id));
    } else if (cmd == "LOGIN") {
        if (v.size() < 3) { sendStr(sock, "ERR|fields"); return; }
        User* u = findUserEmail(v[1]);
        if (!u || u->phash != demoHash(v[2], v[1])) { sendStr(sock, "ERR|bad"); return; }
        sendStr(sock, "OK|" + std::to_string(u->id) + "|" + u->nick);
    } else if (cmd == "POST") {
        if (v.size() < 3) { sendStr(sock, "ERR|fields"); return; }
        int aid; if (!toInt(v[1], aid)) { sendStr(sock, "ERR|badid"); return; }
        if (!findUser(aid)) { sendStr(sock, "ERR|nouser"); return; }
        Post p; p.id = nextPid++; p.author = aid; p.ts = tsNow(); p.text = v[2]; p.img = (v.size() >= 4) ? v[3] : "";
        posts.push_back(p); saveDB();
        std::cout << "[feed] post #" << p.id << " txt=" << p.text.size() << "b img=" << p.img.size() << "b\n";
        sendStr(sock, "OK|" + std::to_string(p.id));
    } else if (cmd == "FEED" || cmd == "ALL") {
        if (v.size() < 2) { sendStr(sock, "ERR|fields"); return; }
        int me; if (!toInt(v[1], me)) { sendStr(sock, "ERR|badid"); return; }
        std::vector<int> vis;
        if (cmd == "ALL") { for (auto& u : users) vis.push_back(u.id); }
        else { vis.push_back(me); for (auto& e : follows) if (e.follower == me) vis.push_back(e.target); }
        for (auto& p : posts) {
            bool ok = false; for (int x : vis) if (x == p.author) { ok = true; break; }
            if (!ok) continue;
            int nl = 0; for (auto& l : likes) if (l.post == p.id) nl++;
            sendStr(sock, "P|" + std::to_string(p.id) + "|" + nickOf(p.author) + "|" + p.ts + "|" + std::to_string(nl) + "|" + p.text + "|" + p.img);
        }
        sendStr(sock, "END");
    } else if (cmd == "LIKE") {
        if (v.size() < 3) { sendStr(sock, "ERR|fields"); return; }
        int pid, uid; if (!toInt(v[1], pid) || !toInt(v[2], uid)) { sendStr(sock, "ERR|badid"); return; }
        bool found = false;
        for (size_t i = 0; i < likes.size(); i++) if (likes[i].post == pid && likes[i].user == uid) { likes.erase(likes.begin() + i); found = true; break; }
        if (!found) likes.push_back({ pid, uid });
        saveDB(); int nl = 0; for (auto& l : likes) if (l.post == pid) nl++; sendStr(sock, "OK|" + std::to_string(nl));
    } else if (cmd == "FOLLOW" || cmd == "UNFOLLOW") {
        if (v.size() < 3) { sendStr(sock, "ERR|fields"); return; }
        User* t = findUserNick(v[1]); if (!t) { sendStr(sock, "ERR|no"); return; }
        int me; if (!toInt(v[2], me)) { sendStr(sock, "ERR|badid"); return; }
        if (cmd == "FOLLOW") { bool has = false; for (auto& e : follows) if (e.follower == me && e.target == t->id) has = true; if (!has) follows.push_back({ me, t->id }); }
        else { for (size_t i = 0; i < follows.size(); i++) if (follows[i].follower == me && follows[i].target == t->id) { follows.erase(follows.begin() + i); break; } }
        saveDB(); sendStr(sock, "OK");
    } else if (cmd == "LIST") {
        for (auto& u : users) sendStr(sock, "U|" + std::to_string(u.id) + "|" + u.nick + "|" + u.email);
        sendStr(sock, "END");
    } else if (cmd == "WHO") {
        if (v.size() < 2) { sendStr(sock, "ERR|fields"); return; }
        int id; if (!toInt(v[1], id)) { sendStr(sock, "ERR|badid"); return; }
        User* u = findUser(id); sendStr(sock, u ? ("OK|" + u->nick) : "ERR|no");
    } else if (cmd == "EXIT") { sendStr(sock, "OK|bye"); close(sock); return; }
    else sendStr(sock, "ERR|unknown");
}
void handleFeed(int sock) {
    std::string acc; char buf[8192];
    while (true) {
        int n = recv(sock, buf, sizeof(buf), 0); if (n <= 0) break;
        acc.append(buf, n); size_t p;
        while ((p = acc.find('\n')) != std::string::npos) {
            std::string line = acc.substr(0, p); acc.erase(0, p + 1);
            if (line.empty()) continue;
            feedCmd(sock, line);
            if (line.rfind("EXIT", 0) == 0) return;
        }
    }
    close(sock);
}
void feedLoop() {
    int ss = socket(AF_INET, SOCK_STREAM, 0);
    int yes = 1; setsockopt(ss, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(yes));
    sockaddr_in a{}; a.sin_family = AF_INET; a.sin_addr.s_addr = INADDR_ANY; a.sin_port = htons(FEED_PORT);
    if (bind(ss, (sockaddr*)&a, sizeof(a)) < 0) { std::cout << "[feed] порт занят\n"; return; }
    listen(ss, 5); std::cout << "[feed] жду на " << FEED_PORT << "\n";
    while (true) { int c = accept(ss, nullptr, nullptr); if (c < 0) continue; std::thread(handleFeed, c).detach(); }
}

int main() {
    loadDB();
    std::cout << "=== Ludarex v3.4 === чат:" << CHAT_PORT << " лента:" << FEED_PORT
              << " | юзеров:" << users.size() << " постов:" << posts.size() << "\n";
    std::thread(feedLoop).detach();
    chatLoop();
    return 0;
}
