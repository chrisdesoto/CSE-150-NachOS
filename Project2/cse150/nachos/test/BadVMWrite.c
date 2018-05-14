#include "stdio.h"
int main(int argc, char *argv[]) {
    int i = 0, size = 0x800;
    char memory_test[size];
    char anotherTest[size];
    int fd;

    fd = creat("test.file");
    memory_test[0] = 'a';
    write(fd,memory_test,1);
    close(fd);

    fd = open("test.file");
    if(read(fd,(void*)0x42424242,1) == -1) {
	printf("Invalid read test succeeded in returning invaild status code\n");
    } else {
	printf("Invalid read test failed in returning an invalid status code\n");
    }

    return 0;
}
