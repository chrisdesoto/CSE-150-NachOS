int main(int argc, char** argv)
{
  int b = argc;
  int a[3];
  a[2] = b;
  while(a[2] != 3)
  {
	a[2] = b;
  }

  return 0;
}
