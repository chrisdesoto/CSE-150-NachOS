#include "stdio.h"

int main(int argc, char *argv[]) {
    int pid, status;
    char prog[] = "exit.coff";
    char* arg = "test";
    char* arg1 = "again";
    char* progArgs[2];
    progArgs[0] = arg;
    progArgs[1] = arg1;

    pid = exec(prog, 2, progArgs);
    printf("-----Num: %d\n",pid);
    printf("Join returned: %d\n", join(pid,&status));
    printf("Status: %d\n", status);

    return status;
}
