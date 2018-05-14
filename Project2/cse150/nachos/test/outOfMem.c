#include "stdio.h"
#include "stdlib.h"

int main(int argc, char *argv[]) {
    // Should cause a stack overflow and an out of memory error
    char a[10];
    int fd = creat("test.file");

    printf("Writing to an invalid address returned %d\n",write(fd, (void*)0x424242, 0x7fffffff));
    return 0;
}
