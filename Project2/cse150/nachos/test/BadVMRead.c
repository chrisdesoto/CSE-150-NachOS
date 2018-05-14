#include "stdio.h"
int main(int argc, char *argv[]) {
    int i = 0, size = 0x800;
    int fd;
    fd = creat("test.file");

    if(write(fd,(void*)0x42424242,size) == -1) {
	printf("[+]Invalid write succeeded in returning a failure status\n");
    } else {
	printf("[-]Invlid write should have returned a failure status\n");
    }
    close(fd);

    return 0;
}
