#include "stdlib.h"
#include "stdio.h"
#include "syscall.h"

#define BUFFER_SIZE 1024
#define PORT_NUMBER 15

int main(int argc, char **argv) {

	char input_buffer[BUFFER_SIZE] = {0};
	char output_buffer[BUFFER_SIZE] = {0};
	char *curr_in_buffer, *curr_out_buffer;
    int i, j, host, fd;

    // Argument logic
    if(argc > 1) {
        printf("Error. Incorrect number of arguments passed.\nPlease specify host (only).\n");
    }

	host = atoi(argv[0]);

	if(host < 0 || host > 255) {
	    printf("Invalid host port: %d\n", host);
	    return 0;
	}

    // Connect to host
    printf("Chat attempting to connect\n");
    fd = connect(host, PORT_NUMBER);
    if(fd < 0) {
        printf("Could not connect to host.\n");
        return 0;
    }
    printf("Chat connected to host %d on port %d with socket file descriptor %d\n", host, PORT_NUMBER, fd);

	while(1) {
        // Buffers for user input and chat msg output
        curr_in_buffer = input_buffer;
        curr_out_buffer = output_buffer;

	    // Check for msg from connection
        if(read(fd, curr_out_buffer, 1) > 0) {
            // Read in chat msg
            while(*curr_out_buffer != '\n') {
                if(read(fd, curr_out_buffer+1, 1) > 0)
                    curr_out_buffer++;
            }
            // Append a null char to end the string
            if(curr_out_buffer < output_buffer+BUFFER_SIZE) {
                *(curr_out_buffer+1) = 0;
            }
            // Print msg to stdout
            printf("%s", output_buffer);
        }

        // Check for user input (in a non-blocking fashion)
        if(read(0, curr_in_buffer, 1) > 0) {
            // Read in all user input available
            while(*curr_in_buffer != '\n') {
                if(read(0, curr_in_buffer+1, 1) > 0)
                    curr_in_buffer++;
            }
            // Append a null char to end the string
            if(curr_in_buffer < input_buffer+BUFFER_SIZE) {
                *(curr_in_buffer+1) = 0;
            }
            // Send user msg to server
            curr_in_buffer = input_buffer;
            while(*curr_in_buffer != 0) {
                if(write(fd, curr_in_buffer, 1) > 0)
                    curr_in_buffer++;
            }
            // Disconnect msg received
            if(input_buffer[0] == '.' && input_buffer[1] == '\n') {
                break;
            }
        }
	}
}