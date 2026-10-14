#include <iostream>
#include <string>
#include <cstring>
#include <thread>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>

const int PORT = 8080;
int sock;
std::string myId;

void sendCmd(const std::string& c) { std::string p = c + "\n"; send(sock, p.c_str(), p.size(), 0); }
std::string recvLine() {
    static std::string acc; char b[4096];
    while (true) {
        size_t p = acc.find('\n');
        if (p != std::string::npos) { std::string r = acc.substr(0, p); acc.erase(0, p + 1); return r; }
        int n = recv(sock, b, sizeof(b), 0);
        if (n <= 0) return "";
        acc.append(b, n);
    }
}
void printPost(const std::string& line) {   // P|pid|nick|ts|likes|text|img
    size_t c1 = line.find('|'); size_t c2 = line.find('|', c1+1); size_t c3 = line.find('|', c2+1);
    size_t c4 = line.find('|', c3+1); size_t c5 = line.find('|', c4+1); size_t c6 = line.find('|', c5+1);
    std::string pid = line.substr(c1+1, c2-c1-1), nick = line.substr(c2+1, c3-c2-1);
    std::string ts = line.substr(c3+1, c4-c3-1), lk = line.substr(c4+1, c5-c4-1);
    std::string text = line.substr(c5+1, c6-c5-1), img = line.substr(c6+1);
    std::cout << "  [" << pid << "] @" << nick << " " << ts << "  ♥" << lk << "\n    " << text;
    if (!img.empty()) std::cout << "  [📷 фото]";
    std::cout << "\n";
}
int main(int argc, char* argv[]) {
    std::string ip = "127.0.0.1"; if (argc > 1) ip = argv[1];
    sock = socket(AF_INET, SOCK_STREAM, 0);
    sockaddr_in a{}; a.sin_family = AF_INET; a.sin_port = htons(PORT);
    inet_pton(AF_INET, ip.c_str(), &a.sin_addr);
    if (connect(sock, (sockaddr*)&a, sizeof(a)) < 0) { std::cout << "сервер не найден\n"; return 1; }
    std::cout << "Ludarex лента. Команды:\n";
    std::cout << "  reg email|nick|pass   login email|pass\n";
    std::cout << "  post текст[@id]       feed / all / list\n";
    std::cout << "  like pid / follow nick / unfollow nick / who / exit\n";
    std::cout << "  (после reg/login id подставится автоматически; @id в post/like/follow - чужой id из list)\n";
    std::string in;
    while (true) {
        std::cout << "> "; std::getline(std::cin, in);
        if (in == "exit") break;
        if (in.empty()) continue;
        std::string cmd, arg;
        size_t sp = in.find(' ');
        if (sp == std::string::npos) { cmd = in; } else { cmd = in.substr(0, sp); arg = in.substr(sp + 1); }
        if (cmd == "reg") {
            sendCmd("REG|" + arg); std::string r = recvLine();
            std::cout << r << "\n";
            if (r.rfind("OK|", 0) == 0) myId = r.substr(3);
        } else if (cmd == "login") {
            sendCmd("LOGIN|" + arg); std::string r = recvLine();
            std::cout << r << "\n";
            if (r.rfind("OK|", 0) == 0) { size_t p = r.find('|', 3); myId = r.substr(3, p - 3); }
        } else if (cmd == "post") {
            if (myId.empty()) { std::cout << "сначала reg/login\n"; continue; }
            sendCmd("POST|" + myId + "|" + arg); std::cout << recvLine() << "\n";
        } else if (cmd == "feed" || cmd == "all") {
            if (myId.empty()) { std::cout << "сначала reg/login\n"; continue; }
            sendCmd((cmd == "feed" ? "FEED|" : "ALL|") + myId);
            std::string r;
            while ((r = recvLine()) != "END" && !r.empty()) { if (r.rfind("P|", 0) == 0) printPost(r); else std::cout << r << "\n"; }
        } else if (cmd == "list") {
            sendCmd("LIST"); std::string r;
            while ((r = recvLine()) != "END" && !r.empty()) {
                if (r.rfind("U|", 0) == 0) { auto v = r.substr(2); std::cout << "  " << v << "\n"; } else std::cout << r << "\n";
            }
        } else if (cmd == "like") {
            if (myId.empty()) { std::cout << "сначала reg/login\n"; continue; }
            sendCmd("LIKE|" + arg + "|" + myId); std::cout << recvLine() << "\n";
        } else if (cmd == "follow" || cmd == "unfollow") {
            if (myId.empty()) { std::cout << "сначала reg/login\n"; continue; }
            sendCmd((cmd == "follow" ? "FOLLOW|" : "UNFOLLOW|") + arg + "|" + myId); std::cout << recvLine() << "\n";
        } else if (cmd == "who") {
            if (myId.empty()) { std::cout << "сначала reg/login\n"; continue; }
            sendCmd("WHO|" + myId); std::cout << recvLine() << "\n";
        } else std::cout << "не знаю такую\n";
    }
    close(sock);
    return 0;
}
