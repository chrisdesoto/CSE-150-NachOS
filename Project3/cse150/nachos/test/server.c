#include "stdlib.h"
#include "syscall.h"

#define BUFFER_SIZE 1024
#define MAX_CLIENTS 16
#define PORT_NUMBER 15

int main(int argc, char **argv) {
	printf("Do the thing!\n");

	char buffer[BUFFER_SIZE] = {0};
	char *curr_buffer;
	int clients[MAX_CLIENTS];
	int fd, fd_idx, bytes_processed;
	int i, j, numRead;

	    // Setup the fd array
	for(i = 0; i < MAX_CLIENTS; i++) {
	    clients[i] = -1;
	}

	while((fd = accept(PORT_NUMBER)) == -1) {}
	if(fd >= 0) {
	    // If client is new, insert into fd array.
	    printf("New client %d\n", fd);
	    clients[0] = fd;
	}
	while(1) {
		// Check each client for msgs
		numRead = read(clients[0], buffer, BUFFER_SIZE);
		if(numRead > 0) {
			printf("NumRead returned %d\n", numRead);
		}
		for(j = 0; j < numRead; ++j) {
			printf("%c",buffer[j]);
			if(buffer[j] == '.') {
				close(clients[i]);
				printf("Closed client\n");
			}
			buffer[j] = 0;
		}
	}
}
