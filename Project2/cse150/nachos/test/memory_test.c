int main(int argc, char *argv[]) {
    int i = 0, size = 30000;
    int memory_test[size];
    for(i=0;i<size;i++){
        memory_test[i] = 56;
    }
    i = 0;
    for(i=0;i<size;i++){
            if(memory_test[i] != 56) {
		printf("Error, assertion failed: %d should equal 56!!\n", memory_test[i]);
		return -1;
	    }
        }
    printf("All tests passed!!\n");
    return 0;
}