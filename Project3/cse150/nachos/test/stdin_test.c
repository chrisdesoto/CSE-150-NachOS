#include "stdio.h"
int main(int argc, char *argv[]) {
    int i = 0, size = 1024, bytes_read = 0;
    char memory_test[size];
    char *p;
    while(1) {
        p = memory_test;
        if(read(0, memory_test, 1) > 0) {
            while(*p != '\n') {
                read(0, ++p, 1);
            }
            if(p < memory_test+1024)
                *(p+1) = 0;
            if(*memory_test == '.' && *(memory_test+1) == '\n') {
                printf("EXIT MESSAGE\n");
                break;
            }
            printf("MESSAGE: %s\n", memory_test);
            printf("TEST SUCCEEDED\n");
        }

    }


    return 0;
}
