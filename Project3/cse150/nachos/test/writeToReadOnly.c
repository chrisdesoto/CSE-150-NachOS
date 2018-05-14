#include "stdio.h"
#include "stdlib.h"

int main(int argc, char *argv[]) {
    int fd;
    fd = creat("test.file");
    write(fd,"abcdef\0",7);
    close(fd);

    fd = open("test.file");
    // Should cause error when writing to an invalid part of memory
    printf("return from writing to read only mem: %d\n", read(fd,0x0,7));

    return 0;
}
