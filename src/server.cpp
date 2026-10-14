#include <iostream>
#include <string>
#include <vector>
#include <map>
#include <fstream>
#include <cstring>
#include <thread>
#include <mutex>
#include <sys/socket.h>
#include <netinet/in.h>
#include <unistd.h>

const int PORT = 9999;
const std::string DB_FILE = "chat_db.txt";

std::vector<std::string> history;
std::mutex histMtx;
std::map<std::string, int> nameToSock;
std::mutex nameMtx;
std::vector<int> clients;
std::mutex clientsMtx;

void saveHistory() {
    std::ofstream f(DB_FILE);
    for (const std::string& s : history) f << s << "\n";
}
void loadHistory() {
    history.clear();
    std::ifstream f(DB_FILE);
    std::string s;
    while (std::getline(f, s)) if (!s.empty()) history.push_back(s);
}
void sendAll(const std::string& t) {
    std::lock_guard<std::mutex> lk(clientsMtx);
    for (int c : clients) send(c, t.c_str(), t.size(), 0);
}
void sendTo(const std::string& name, const std::string& t) {
    std::lock_guard<std::mutex> lk(nameMtx);
    auto it = nameToSock.find(name);
    if (it != nameToSock.end()) send(it->second, t.c_str(), t.size(), 0);
}
void dropFromClients(int s) {
    std::lock_guard<std::mutex> lk(clientsMtx);
    for (size_t i = 0; i < clients.size(); i++)
        if (clients[i] == s) { clients.erase(clients.begin() + i); return; }
}
void handleClient(int sock) {
    std::string acc;
    char buf[8192];
    std::string myName;
    bool registered = false;
    while (true) {
        int n = recv(sock, buf, sizeof(buf), 0);
        if (n <= 0) break;
        acc.append(buf, n);
        size_t p;
        while ((p = acc.find('\n')) != std::string::npos) {
            std::string line = acc.substr(0, p);
            acc.erase(0, p + 1);
            if (line.empty()) continue;
            if (!registered) {
                if (line.rfind("REG ", 0) == 0) {
                    myName = line.substr(4);
                    int old = -1;
                    { std::lock_guard<std::mutex> lk(nameMtx);
                      auto it = nameToSock.find(myName);
                      if (it != nameToSock.end() && it->second != sock) { old = it->second; it->second = sock; }
                      else nameToSock[myName] = sock; }
                    if (old != -1) { dropFromClients(old); shutdown(old, SHUT_RDWR); }
                    { std::lock_guard<std::mutex> lk(clientsMtx); clients.push_back(sock); }
                    registered = true;
                    sendAll("=== " + myName + " в сети ===\n");
                }
                continue;
            }
            if (line[0] == '@') {
                size_t sp = line.find(' ');
                if (sp == std::string::npos) continue;
                sendTo(line.substr(1, sp - 1), line.substr(sp + 1) + "\n");
            } else if (line == "/history") {
                std::string reply;
                { std::lock_guard<std::mutex> lk(histMtx);
                  for (const std::string& s : history) reply += s + "\n"; }
                if (reply.empty()) reply = "История пуста\n";
                send(sock, reply.c_str(), reply.size(), 0);
            } else if (line == "/clear") {
                { std::lock_guard<std::mutex> lk(histMtx); history.clear(); saveHistory(); }
                sendAll("=== сервер очистил чат ===\n");
            } else {
                { std::lock_guard<std::mutex> lk(histMtx); history.push_back(line); saveHistory(); }
                sendAll(line + "\n");
            }
        }
    }
    bool wasActive = false;
    { std::lock_guard<std::mutex> lk(nameMtx);
      auto it = nameToSock.find(myName);
      if (it != nameToSock.end() && it->second == sock) { nameToSock.erase(it); wasActive = !myName.empty(); } }
    dropFromClients(sock);
    if (wasActive) sendAll("=== " + myName + " вышел ===\n");
    close(sock);
}
int main() {
    loadHistory();
    std::cout << "=== СЕРВЕР Ludarex v3.2 (чистая история) === строк: " << history.size() << "\n";
    int ss = socket(AF_INET, SOCK_STREAM, 0);
    int yes = 1; setsockopt(ss, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(yes));
    sockaddr_in a{}; a.sin_family = AF_INET; a.sin_addr.s_addr = INADDR_ANY; a.sin_port = htons(PORT);
    if (bind(ss, (sockaddr*)&a, sizeof(a)) < 0) { std::cout << "Порт занят!\n"; return 1; }
    listen(ss, 5);
    std::cout << "[server] жду клиентов на порту " << PORT << "...\n";
    while (true) {
        int c = accept(ss, nullptr, nullptr);
        if (c < 0) continue;
        std::thread(handleClient, c).detach();
    }
    return 0;
}
