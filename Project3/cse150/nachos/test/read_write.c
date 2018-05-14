#include "stdio.h"
int main(int argc, char *argv[]) {
    int i = 0, size = 0x800;
    char memory_test[size];
    char anotherTest[size];
    int fd;
    fd = creat("test.file");
    for(i = 0; i < size; ++i) {
        memory_test[i] = 'a';
    }
    write(fd,memory_test,size);
    close(fd);

    fd = open("test.file");
    read(fd,anotherTest,size);

    printf("TEST SUCCEEDED");
    return 0;
}
