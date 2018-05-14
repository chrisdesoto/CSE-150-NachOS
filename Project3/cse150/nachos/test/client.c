#include "stdlib.h"
#include "stdio.h"
#include "syscall.h"

#define BUFFER_SIZE 1024
#define PORT_NUMBER 15

int main(int argc, char **argv) {

	char input_buffer[BUFFER_SIZE] = {0};
	char output_buffer[BUFFER_SIZE] = {0};
	char *curr_in_buffer, *curr_out_buffer;
    int host, fd, fd_idx, byte_count, bytes_processed;
    int i, j, recv_input;

	host = 0;
    fd = connect(host, PORT_NUMBER);
    if(fd < 0) {
        printf("Could not connect to host.\n");
        return 0;
    } else {
	printf("connected\n");
    }
    output_buffer[0] = 'h';
    printf("Wrote %d bytes\n", write(fd,output_buffer,1));

    // Received input flag (needed?)
    recv_input = 0;

	while(1) {
		// Check for user input (in a non-blocking fashion)
		recv_input = read(0,output_buffer,BUFFER_SIZE);
		i = 0;
		while(i < recv_input) {
			j = write(fd,&output_buffer[i],recv_input-i);
			if(j != -1) {
				i += j;
			}
		}

	}
}
