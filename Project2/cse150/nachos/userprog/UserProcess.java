package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;

import java.io.EOFException;
import java.util.HashMap;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 * <p>
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */
    public UserProcess() {
        signaller = new Semaphore(0);
        //Initialize the array of open files to null. Max 16 open files per process.
        this.openFiles = new OpenFile[MAX_OPEN_FILES];
        // Initialize Std. In and Std. Out using file descriptors 0 and 1
        this.openFiles[0] = UserKernel.console.openForReading();
        this.openFiles[1] = UserKernel.console.openForWriting();

        int numPhysPages = Machine.processor().getNumPhysPages();
        pageTable = new TranslationEntry[numPhysPages];

        UserKernel.incNumProcs();
    }

    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
        return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param name the name of the file containing the executable.
     * @param args the arguments to pass to the executable.
     * @return <tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
        if (!load(name, args))
            return false;

        new UThread(this).setName(name).fork();

        return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
        Machine.processor().setPageTable(pageTable);
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param vaddr     the starting virtual address of the null-terminated
     *                  string.
     * @param maxLength the maximum number of characters in the string,
     *                  not including the null terminator.
     * @return the string read, or <tt>null</tt> if no null terminator was
     * found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
        Lib.assertTrue(maxLength >= 0);

        byte[] bytes = new byte[maxLength + 1];

        int bytesRead = readVirtualMemory(vaddr, bytes);

        for (int length = 0; length < bytesRead; length++) {
            if (bytes[length] == 0)
                return new String(bytes, 0, length);
        }

        return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param vaddr the first byte of virtual memory to read.
     * @param data  the array where the data will be stored.
     * @return the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
        return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param vaddr  the first byte of virtual memory to read.
     * @param data   the array where the data will be stored.
     * @param offset the first byte to write in the array.
     * @param length the number of bytes to transfer from virtual memory to
     *               the array.
     * @return the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset,
                                 int length) {
        // TODO harden and get rid of assert
        if(!(offset >= 0 && length >= 0 && offset + length <= data.length)) {
            return 0;
        }

        byte[] memory = Machine.processor().getMemory();
        int bytesRead = 0;
        while(bytesRead < length) {

            int vpn = (vaddr+bytesRead) / pageSize;
            int virtual_offset = (vaddr+bytesRead) % pageSize;

            if(vpn < 0 || vpn >= pageTable.length) {
                return 0;
            }
            TranslationEntry new_entry = pageTable[vpn];
            if(new_entry == null || new_entry.valid == false) {
                System.out.println("VPN " + vpn + " Entry not valid");
                return 0;
            }

            int physical_addr = new_entry.ppn * pageSize + virtual_offset;
            //System.out.println("Physical address: " + physical_addr);
            if (physical_addr < 0 || physical_addr >= memory.length)
                return 0;

            int limit = (new_entry.ppn + 1)*pageSize;
            int amount = Math.min(limit-physical_addr, Math.min(length-bytesRead, memory.length - physical_addr));

            System.arraycopy(memory, physical_addr, data, offset+bytesRead, amount);
            bytesRead += amount;
        }

        return bytesRead;
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param vaddr the first byte of virtual memory to write.
     * @param data  the array containing the data to transfer.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
        return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param vaddr  the first byte of virtual memory to write.
     * @param data   the array containing the data to transfer.
     * @param offset the first byte to transfer from the array.
     * @param length the number of bytes to transfer from the array to
     *               virtual memory.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset,
                                  int length) {
        if(!(offset >= 0 && length >= 0 && offset + length <= data.length)) {
            return 0;
        }

        byte[] memory = Machine.processor().getMemory();
        int bytesWritten = 0;
        while(bytesWritten < length) {

            int vpn = (vaddr+bytesWritten) / pageSize;
            //System.out.println("WRITE VPN: " + vpn);
            int virtual_offset = (vaddr+bytesWritten) % pageSize;
            if(vpn < 0 || vpn >= pageTable.length) {
                return 0;
            }

            TranslationEntry new_entry = pageTable[vpn];
            if(new_entry == null || new_entry.valid == false) {
                //System.out.println("---------Creating new page entry");
                new_entry.ppn = UserKernel.get_free_page();
                new_entry.valid = true;
            }
            if(new_entry.readOnly == true) {
                return 0;
            }
            new_entry.used = true;

            int physical_addr = new_entry.ppn * pageSize + virtual_offset;
            if (physical_addr < 0 || physical_addr >= memory.length)
                return 0;
            new_entry.dirty = true;

            int limit = (new_entry.ppn + 1)*pageSize;
            int amount = Math.min((limit-physical_addr), Math.min(length - bytesWritten, memory.length - physical_addr));

            System.arraycopy(data, offset+bytesWritten, memory, physical_addr, amount);
            bytesWritten += amount;
        }

        return length;
    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param name the name of the file containing the executable.
     * @param args the arguments to pass to the executable.
     * @return <tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
        Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

        OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
        if (executable == null) {
            Lib.debug(dbgProcess, "\topen failed");
            return false;
        }

        try {
            coff = new Coff(executable);
        } catch (EOFException e) {
            executable.close();
            Lib.debug(dbgProcess, "\tcoff load failed");
            return false;
        }

        // make sure the sections are contiguous and start at page 0
        numPages = 0;
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);
            if (section.getFirstVPN() != numPages) {
                coff.close();
                Lib.debug(dbgProcess, "\tfragmented executable");
                return false;
            }
            numPages += section.getLength();
        }

        // make sure the argv array will fit in one page
        byte[][] argv = new byte[args.length][];
        int argsSize = 0;
        for (int i = 0; i < args.length; i++) {
            argv[i] = args[i].getBytes();
            // 4 bytes for argv[] pointer; then string plus one for null byte
            argsSize += 4 + argv[i].length + 1;
        }
        if (argsSize > pageSize) {
            coff.close();
            Lib.debug(dbgProcess, "\targuments too long");
            return false;
        }

        // program counter initially points at the program entry point
        initialPC = coff.getEntryPoint();

        // next comes the stack; stack pointer initially points to top of it
        numPages += stackPages;
        initialSP = numPages * pageSize;

        // and finally reserve 1 page for arguments
        numPages++;

        if (!loadSections())
            return false;

        // store arguments in last page
        int entryOffset = (numPages - 1) * pageSize;
        int stringOffset = entryOffset + args.length * 4;

        this.argc = args.length;
        this.argv = entryOffset;

        for (int i = 0; i < argv.length; i++) {
            byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
            Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
            entryOffset += 4;
            Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
                    argv[i].length);
            stringOffset += argv[i].length;
            Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[]{0}) == 1);
            stringOffset += 1;
        }

        return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return <tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
        if (numPages > Machine.processor().getNumPhysPages()) {
            coff.close();
            Lib.debug(dbgProcess, "\tinsufficient physical memory");
            return false;
        }

        // load sections
        int loadedPages = 0;
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);

            Lib.debug(dbgProcess, "\tinitializing " + section.getName()
                    + " section (" + section.getLength() + " pages)");

            for (int i = 0; i < section.getLength(); i++) {
                int vpn = section.getFirstVPN() + i;
                if(pageTable[vpn] == null) {
                    pageTable[vpn] = new TranslationEntry(vpn, UserKernel.get_free_page(), true, false, false, false);
                    loadedPages++;
                }
                TranslationEntry new_entry = pageTable[vpn];

                new_entry.used = true;
                new_entry.readOnly = section.isReadOnly();
                // for now, just assume virtual addresses=physical addresses
                section.loadPage(i, new_entry.ppn);
            }
        }

        for (int i = loadedPages; i <= loadedPages + 8; i++) {
            pageTable[i] = new TranslationEntry(i, UserKernel.get_free_page(), true, false, false, false);
        }

        return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
        for (int i = 0; i < numPages; i++) {
            if(pageTable[i] != null) {
                pageTable[i].valid = false;
                UserKernel.add_free_page(pageTable[i].ppn);
            }
        }
    }

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
        Processor processor = Machine.processor();

        // by default, everything's 0
        for (int i = 0; i < processor.numUserRegisters; i++)
            processor.writeRegister(i, 0);

        // initialize PC and SP according
        processor.writeRegister(Processor.regPC, initialPC);
        processor.writeRegister(Processor.regSP, initialSP);

        // initialize the first two argument registers to argc and argv
        processor.writeRegister(Processor.regA0, argc);
        processor.writeRegister(Processor.regA1, argv);
    }


    /**
     * Handle the halt() system call.
     */
    private int handleHalt() {
        if (pid == 0) {
            Machine.halt();
        } else {
            return -1;
        }

        Lib.assertNotReached("Machine.halt() did not halt machine!");
        return 0;
    }

    void handleExit(int status) {
        // TODO harden
        // Status is passed by child and should be passed up to whoever is calling join

        // Dealloc file descriptors
        // NOTE: Do not use any file descriptors after this point
        for (int i = 0; i < openFiles.length; ++i) {
            if (openFiles[i] != null) {
                openFiles[i].close();
            }
        }

        // Go through each child process and remove the pointer to the parent
        for(Integer i : children.keySet()) {
            children.get(i).parentPID = -1;
        }

        // Deallocate Virtual memory
        unloadSections();

        // set the status code (Possibly stored in this program, so that the parent process points here
        statusOnExit = status;
        UserKernel.exited(this, status);

        // If this is the root process, then call finish on machine
        if (UserKernel.getNumProcs() == 0) {
            Kernel.kernel.terminate();
        }

        // Signal to parent that this process called exit
        signaller.V();
        //System.out.println("Child " + pid + " called V to wake parent");

        UThread.currentThread().finish();
    }

    int handleExec(int fnamePtr, int argc, int argvPtrPtr) {
        // TODO harden
        //System.out.println("Handling exec!");
        // Retrieve filename of executable, then sanity check it and argument length
        String filename = readVirtualMemoryString(fnamePtr, 256);
        //System.out.println("Virtual mem string: " + filename);

        if (argc >= 0 && filename != null && filename.indexOf(".coff") == filename.length() - ".coff".length()) {

            // Obtain the pointers to the argv char buffers
            int[] argvPtr = new int[argc];
            byte[] argvb = new byte[argc * 4];
            int offset = 0;
            while (offset < argc * 4) {
                offset += readVirtualMemory(argvPtrPtr, argvb, offset, (argc * 4) - offset);
            }
            for (int i = 0; i < argc; ++i) {
                argvPtr[i] = Lib.bytesToInt(argvb, 4 * i, 4);
                //System.out.println("Argv pointer: " + argvPtr[i]);
            }

            // Retrieve the contents of argv and store them in a string array
            String[] argv = new String[argc];
            for (int i = 0; i < argc; ++i) {
                argv[i] = readVirtualMemoryString(argvPtr[i], 256);
                //System.out.println("Argv " + i + " " + argv[i]);
            }

            // Set up new process and initialize other variables and file descriptor
            // Set up new process' state, declare it as a child, and initialize required variables
            UserProcess child = newUserProcess();
            child.pid = UserKernel.newPID();
            child.parentPID = pid;
            children.put(child.pid, child);
            /*
            // I/O file descriptors have already been initialized in the child's constructor,
            // so we will now duplicate the parent's file descriptors
            for (int i = 2; i < openFiles.length; ++i) {
                if (openFiles[i] != null) {
                    OpenFile childCopy = UserKernel.fileSystem.open(openFiles[i].getName(), false);

                    if (childCopy == null) {
                        System.out.println("ERROR OCCURRED ON FILE DESCRIPTOR COPY!!!!");
                        return -1;
                    }

                    childCopy.seek(openFiles[i].tell());
                    child.openFiles[i] = childCopy;
                }
            }
            */
            if(child.execute(filename, argv)) {
                return child.pid;
            } else {
                child.parentPID = -1;
                children.remove(child.pid);
                UserKernel.exited(child,-1);
                return -1;
            }
        } else {
            //Error with filename
            System.out.println("Error with filename");
            return -1;
        }
    }

    int handleJoin(int procId, int statusPtr) {
        if (!children.containsKey(procId)) {
            System.out.println("Theres no child with id: " + procId);
            return -1;
        }

        // Call child process's semaphore to wait for it to call exit
        // If it already called exit, then it has already called V and P
        // will automatically return.
        //System.out.println("Going to sleep to wait for child to wake");
        children.get(procId).signaller.P();
        //System.out.println("Parent process " + pid + " woke after exit was called");

        // Write status value
        int status = children.get(procId).statusOnExit;
        if(writeVirtualMemory(statusPtr, Lib.bytesFromInt(status), 0, 4) != 4) {
            return -1;
        }
        //System.out.println("Status is: " + status);
        children.remove(procId);

        if (status != -1) {
            return 1;
        }

        return 0;
    }


    /* FILE MANAGEMENT SYSCALLS: creat, open, read, write, close, unlink
     *
     * A file descriptor is a small, non-negative integer that refers to a file on
     * disk or to a stream (such as console input, console output, and network
     * connections). A file descriptor can be passed to read() and write() to
     * read/write the corresponding file/stream. A file descriptor can also be
     * passed to close() to release the file descriptor and any associated
     * resources.
     */

    /**
     * Handle the creat() system call.
     * <p>
     * Attempt to open the named disk file, creating it if it does not exist,
     * and return a file descriptor that can be used to access the file.
     * <p>
     * Note that creat() can only be used to create files on disk; creat() will
     * never return a file descriptor referring to a stream.
     * <p>
     * Returns the new file descriptor, or -1 if an error occurred.
     */
    private int handleCreat(int nameAddr) {

        // Find open file descriptor and assign
        // Translate the memory address of the filename string
        // Open file with the provided StubFileSystem passing create flag (true)
        // Return assigned file descriptor

        int fileDescriptor = -1;

        for (int i = 0; i < this.openFiles.length; i++)
            if (openFiles[i] == null)
                fileDescriptor = i;

        if (fileDescriptor == -1)
            return -1;

        String filename = readVirtualMemoryString(nameAddr, MAX_FILENAME_BYTE_SIZE);

        if (filename == null || filename.length() == 0)
            return -1;

        OpenFile newFile;

        newFile = UserKernel.fileSystem.open(filename, true);

        if (newFile == null)
            return -1;

        this.openFiles[fileDescriptor] = newFile;

        return fileDescriptor;
    }

    /**
     * Handle the open() system call.
     * <p>
     * Attempt to open the named file and return a file descriptor.
     * <p>
     * Note that open() can only be used to open files on disk; open() will never
     * return a file descriptor referring to a stream.
     * <p>
     * Returns the new file descriptor, or -1 if an error occurred.
     */
    private int handleOpen(int nameAddr) {

        // Find open file descriptor and assign
        // Translate the memory address of the filename string
        // Open file with the provided StubFileSystem passing create flag (false)
        // Return assigned file descriptor

        int fileDescriptor = -1;

        for (int i = 0; i < this.openFiles.length; i++)
            if (openFiles[i] == null)
                fileDescriptor = i;

        if (fileDescriptor == -1)
            return -1;

        String filename = readVirtualMemoryString(nameAddr, MAX_FILENAME_BYTE_SIZE);

        if (filename == null || filename.length() == 0)
            return -1;

        OpenFile openFile;

        openFile = UserKernel.fileSystem.open(filename, false);

        if (openFile == null)
            return -1;

        this.openFiles[fileDescriptor] = openFile;

        return fileDescriptor;

    }

    /**
     * Handle the read() system call.
     * <p>
     * Attempt to read up to count bytes into buffer from the file or stream
     * referred to by fileDescriptor.
     * <p>
     * On success, the number of bytes read is returned. If the file descriptor
     * refers to a file on disk, the file position is advanced by this number.
     * <p>
     * It is not necessarily an error if this number is smaller than the number of
     * bytes requested. If the file descriptor refers to a file on disk, this
     * indicates that the end of the file has been reached. If the file descriptor
     * refers to a stream, this indicates that the fewer bytes are actually
     * available right now than were requested, but more bytes may become available
     * in the future. Note that read() never waits for a stream to have more data;
     * it always returns as much as possible immediately.
     * <p>
     * On error, -1 is returned, and the new file position is undefined. This can
     * happen if fileDescriptor is invalid, if part of the buffer is read-only or
     * invalid, or if a network stream has been terminated by the remote host and
     * no more data is available.
     */
    private int handleRead(int fileDescriptor, int buffer, int count) {
        if(count < 0)
            return -1;

        // Check that buffer is a valid address before allocating a giant byte buffer
        byte[] addrTestBuff = new byte[1];
        if(readVirtualMemory(buffer,addrTestBuff,0,1) != 1) {
            return -1;
        }

        if(fileDescriptor < 0 || fileDescriptor > 15)
            return -1;

        if(openFiles[fileDescriptor] == null)
            return -1;

        byte[] bytes = new byte[count];
        int bytesRead = 0;

        // Grab the file, read the correct number of bytes to a new array
        while(bytesRead < count) {
            int read = openFiles[fileDescriptor].read(bytes, bytesRead, count - bytesRead);
            if(read == -1) {
                return -1;
            }
            bytesRead += read;
        }


        // Write the read bytes to virtual memory
        if(writeVirtualMemory(buffer, bytes, 0, bytesRead) != bytesRead) {
            return -1;
        }

        return bytesRead;
    }

    /**
     * Handle the write() system call.
     * <p>
     * Attempt to write up to count bytes from buffer to the file or stream
     * referred to by fileDescriptor. write() can return before the bytes are
     * actually flushed to the file or stream. A write to a stream can block,
     * however, if kernel queues are temporarily full.
     * <p>
     * On success, the number of bytes written is returned (zero indicates nothing
     * was written), and the file position is advanced by this number. It IS an
     * error if this number is smaller than the number of bytes requested. For
     * disk files, this indicates that the disk is full. For streams, this
     * indicates the stream was terminated by the remote host before all the data
     * was transferred.
     * <p>
     * On error, -1 is returned, and the new file position is undefined. This can
     * happen if fileDescriptor is invalid, if part of the buffer is invalid, or
     * if a network stream has already been terminated by the remote host.
     */
     // <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
    private int handleWrite(int fd, int bufferPtr, int size) {
        if(size < 0) {
            return -1;
        }
        // Check that bufferPtr is valid before allocating a giant byte buffer
        byte[] addrTestBuff = new byte[1];
        if(readVirtualMemory(bufferPtr,addrTestBuff,0,1) != 1) {
            return -1;
        }

        byte[] buffer = new byte[size];
        int read = 0;
        int numReadZero = 0;
        while(read < size) {
            int numRead = readVirtualMemory(bufferPtr,buffer, read, size-read);
            read += numRead;

            // readVirtualMemory handles sanity checks on the addresses and returns 0 upon error,
            // so if > 2 zeros are received in a row, something is probably wrong, and this should
            // return an error
            if(numReadZero > 0 && numRead != 0) {
                numReadZero = 0;
            }
            if(numRead == 0) {
                if(++numReadZero > 2) {
                    return -1;
                }
            }
        }

        int written = 0;
        if(fd >= 0 && fd < openFiles.length && openFiles[fd] != null) {
            int countStuck = 0;
            while(written < size && countStuck < 3) {
                int thisWritten = openFiles[fd].write(buffer,written,size-written);
                if(thisWritten == -1) {
                    return -1;
                }

                written += thisWritten;
                if(thisWritten == 0) {
                    countStuck++;
                }
            }
            if(countStuck == 3) {
                return written;
            }
        } else {
            return -1;
        }

        return written;
    }

    /**
     * Handle the close() system call.
     * <p>
     * Close a file descriptor, so that it no longer refers to any file or stream
     * and may be reused.
     * <p>
     * If the file descriptor refers to a file, all data written to it by write()
     * will be flushed to disk before close() returns.
     * If the file descriptor refers to a stream, all data written to it by write()
     * will eventually be flushed (unless the stream is terminated remotely), but
     * not necessarily before close() returns.
     * <p>
     * The resources associated with the file descriptor are released. If the
     * descriptor is the last reference to a disk file which has been removed using
     * unlink, the file is deleted (this detail is handled by the file system
     * implementation).
     * <p>
     * Returns 0 on success, or -1 if an error occurred.
     */
    private int handleClose(int fileDescriptor) {

        // Check if valid file descriptor
        // Retrieve the corresponding file
        // Close the file
        // Free the descriptor

        if(fileDescriptor < 0 || fileDescriptor > 15)
            return -1;

        OpenFile fileToClose = this.openFiles[fileDescriptor];

        if(fileToClose == null || fileToClose.length() < 0)
            return -1;

        fileToClose.close();

        if(fileToClose.length() != -1)
            return -1;

        this.openFiles[fileDescriptor] = null;

        return 0;

    }

    /**
     * Handle the unlink() system call.
     * <p>
     * Delete a file from the file system. If no processes have the file open, the
     * file is deleted immediately and the space it was using is made available for
     * reuse.
     * <p>
     * If any processes still have the file open, the file will remain in existence
     * until the last file descriptor referring to it is closed. However, creat()
     * and open() will not be able to return new file descriptors for the file
     * until it is deleted.
     * <p>
     * Returns 0 on success, or -1 if an error occurred.
     */
    private int handleUnlink(int nameAddr) {

        // Translate the memory address of the filename string
        // Call the remove function on the file and get the result

        String filename = readVirtualMemoryString(nameAddr, MAX_FILENAME_BYTE_SIZE);

        if (filename == null || filename.length() == 0)
            return -1;

        boolean isClosed = UserKernel.fileSystem.remove(filename);

        if (!isClosed)
            return -1;

        return 0;

    }

    private static final int
            syscallHalt = 0,
            syscallExit = 1,
            syscallExec = 2,
            syscallJoin = 3,
            syscallCreate = 4,
            syscallOpen = 5,
            syscallRead = 6,
            syscallWrite = 7,
            syscallClose = 8,
            syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     * <p>
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * </tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     * </tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     * </tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     *
     * @param syscall the syscall number.
     * @param a0      the first syscall argument.
     * @param a1      the second syscall argument.
     * @param a2      the third syscall argument.
     * @param a3      the fourth syscall argument.
     * @return the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
        //System.out.println("PID: " + pid);
        //System.out.println("Got syscall " + syscall);
        switch (syscall) {
            case syscallHalt:
                return handleHalt();
            case syscallExit:
                handleExit(a0);
            case syscallExec:
                return handleExec(a0, a1, a2);
            case syscallJoin:
                return handleJoin(a0, a1);
            case syscallCreate:
                return handleCreat(a0);
            case syscallOpen:
                return handleOpen(a0);
            case syscallRead:
                return handleRead(a0, a1, a2);
            case syscallWrite:
                return handleWrite(a0, a1, a2);
            case syscallClose:
                return handleClose(a0);
            case syscallUnlink:
                return handleUnlink(a0);

            default:
                Lib.debug(dbgProcess, "Unknown syscall " + syscall);
                Lib.assertNotReached("Unknown system call!");
        }

        return 0;
    }

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param cause the user exception that occurred.
     */
    public void handleException(int cause) {
        Processor processor = Machine.processor();

        switch (cause) {
            case Processor.exceptionSyscall:
                int result = handleSyscall(processor.readRegister(Processor.regV0),
                        processor.readRegister(Processor.regA0),
                        processor.readRegister(Processor.regA1),
                        processor.readRegister(Processor.regA2),
                        processor.readRegister(Processor.regA3)
                );
                processor.writeRegister(Processor.regV0, result);
                processor.advancePC();
                break;

            default:
                // TODO insert exit here?
                Lib.debug(dbgProcess, "Unexpected exception: " +
                        Processor.exceptionNames[cause]);
                System.out.println("Unexpected exception: " + Processor.exceptionNames[cause]);
                System.out.println("Received exception, exiting with error based status");
                handleExit(-1);
                Lib.assertNotReached("Unexpected exception");
        }
    }

    /**
     * The program being run by this process.
     */
    protected Coff coff;

    /**
     * This process's page table.
     */
    protected TranslationEntry[] pageTable;
    /**
     * The number of contiguous pages occupied by the program.
     */
    protected int numPages;

    /**
     * The number of pages in the program's stack.
     */
    protected final int stackPages = 8;

    private int initialPC, initialSP;
    private int argc, argv;

    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';

    private HashMap<Integer, UserProcess> children = new HashMap<Integer, UserProcess>();
    private int pid = 0;
    public int parentPID = -1;
    public int statusOnExit;
    public Semaphore signaller;

    // Increment each time a new PID is created and before incrementing,
    // set the pid
    public static int nextPID = 1;

    private static final int MAX_FILENAME_BYTE_SIZE = 256;
    private static final int MAX_OPEN_FILES = 16;
    private OpenFile[] openFiles;
}
