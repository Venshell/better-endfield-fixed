#include "../../shared/third_party_modules/third_party_host.h"

#include <iostream>

int main(int argc, char** argv) {
    if (argc != 2) return 2;
    BetterEndfieldNext::ThirdParty::ThirdPartyHost host;
    if (!host.Start(argv[1], "android-arm64",
            [](const std::string& id, const std::string& message) {
                std::cerr << id << ": " << message << '\n';
            })) return 1;
    std::cout << "ready\n" << std::flush;
    std::string line;
    std::getline(std::cin, line);
    host.Stop();
    return 0;
}
