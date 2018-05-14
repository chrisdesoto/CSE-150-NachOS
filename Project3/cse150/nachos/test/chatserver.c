#include "stdlib.h"
#include "syscall.h"

#define BUFFER_SIZE 1024
#define MAX_CLIENTS 16
#define PORT_NUMBER 15

int main(int argc, char **argv) {

	char buffer[BUFFER_SIZE] = {0};
	char *curr_buffer;
	int clients[MAX_CLIENTS];
	int i, j, fd;

    // Setup the fd array
	for(i = 0; i < MAX_CLIENTS; i++) {
	    clients[i] = -1;
	}

	printf("Accepting connections\n");

	while(1) {
	    // Accept connections on port 15
	    fd = accept(PORT_NUMBER);
        if(fd > 0) {
            // If client is new, insert into fd array.
            for(i = 0; i < MAX_CLIENTS; i++) {
                if(clients[i] == -1) {
                    clients[i] = fd;
	                printf("Accepted connection on index %d with file descriptor %d\n", i, fd);
	                break;
                }
            }
        }
        // Check each client for msgs
        for(i = 0; i < MAX_CLIENTS; ++i) {
            if(clients[i] >= 0 && read(clients[i], buffer, 1) > 0) {
                // Read the msg into buffer from file
                curr_buffer = buffer;
                while(*curr_buffer != '\n') {
                    if(read(clients[i], curr_buffer+1, 1) > 0)
                        curr_buffer++;
                }
                if(curr_buffer < buffer+BUFFER_SIZE)
                    *(curr_buffer+1) = 0;
                printf("Message: %s", buffer);
                // If buffer contains the disconnect msg, remove the client.
                if(buffer[0] == '.' && buffer[1] == '\n') {
                    // If close succeeds free up the descriptor
                    if(close(clients[i]) == 0) {
                        for(j = 0; j < MAX_CLIENTS; j++)
                            if(clients[j] == clients[i])
                                clients[i] = -1;
                        printf("Client socket closed and descriptor slot freed\n");
                        continue;
                    } else {
                        printf("Error client socket not properly closed.\n", clients[i]);
                        return 0;
                    }
                }
                // Broadcast the message using write.
                for(j = 0; j < MAX_CLIENTS; j++) {
                    if(clients[j] >= 0 && j != i) {
                        printf("Broadcasting to client %d on socket %d\n", j, clients[j]);
                        curr_buffer = buffer;
                        while(*curr_buffer != 0) {
                            if(write(clients[j], curr_buffer, 1) > 0)
                                curr_buffer++;
                        }
                    }
                }
                printf("Done broadcasting\n");
            }
        }
	}
}
