package dev.betterendfield.next;

interface IGameControlService {
    int forceStop(in String packageName) = 1;
    void destroy() = 16777114;
}
