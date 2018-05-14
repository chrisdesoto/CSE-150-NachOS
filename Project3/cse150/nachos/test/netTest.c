int main(int argc, char** argv)
{
  int fd;
  char prog[] = "netTest.coff";
    char* arg = "test";
    char* progArgs[1];
    progArgs[0] = arg;
  int argBuff[1];
  int hostNum = 0, portNum = 42;
  printf("______________RUNNING PROGRAM________________\n");
  if(argc >= 1) {
    printf("CLIENT\n");
     //should be client
     fd = connect(hostNum,portNum);
  } else {
    printf("PARENT\n");
    exec(prog, 1, progArgs);
    while((fd = accept(portNum)) == -1);
    printf("Accepted fd: %d\n",fd);
  }

  return 0;
}
