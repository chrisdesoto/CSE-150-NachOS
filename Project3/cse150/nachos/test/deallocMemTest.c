#include "stdio.h"
#include "stdlib.h"

int main(int argc, char *argv[]) {
    int pid, status;
    char prog[] = "exit.coff";
    char* arg = "test";
    char* arg1 = "again";
    char* progArgs[2];
    int i = 0, numTimes = 1000;
    progArgs[0] = arg;
    progArgs[1] = arg1;

    while(i++ < numTimes) {
        status = 0;
        join(exec(prog, 2, progArgs),&status);
        assert(status == 42);
        printf("Successfully executed and joined for the %d time\n",i);
    }
    printf("Testing that exit deallocates pages works");
    return status;
}
