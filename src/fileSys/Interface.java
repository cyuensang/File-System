package fileSys;

import java.util.ArrayList;

import fileSys.FSConst;

public class Interface 
{
	private byte[][] oft;
	protected int[][] oftVar;
	
	protected ArrayList<Byte> mainMem;
	protected Disk d;
	
	public Interface()
	{
		init_all();
	}
	
	public void create(String filename)
	{
		check_valid_filename(filename);
		ArrayList<Byte> hold = new ArrayList<>();
		PackableMemory b = new PackableMemory(4);
		int fdIndex[] = available_FD();
		int fileSlotIndex;
		
		copy_memory_to(hold);
		fileSlotIndex = get_available_dirSlot();
		
		// assign filename and file descriptor index in directory
		for(int i = 0; i < 4; i++) // pad with 0s
			mainMem.set(i, (byte) 0);
		for(int j = 0; j < filename.length(); j++) // write filename
			mainMem.set(j, (byte)filename.charAt(j));
		
		// convert the FD index to bytes array
		b.pack(get_FD_index(fdIndex), 0);
		for(int k = 4; k < 8; k++) // write FD index in main memory
			mainMem.set(k, b.mem[k-4]);
		
		//write(0, mainMem, FSConst.MAX_FILE_SIZE);
		lseek(0, fileSlotIndex);
		write(0, mainMem, mainMem.size());
		restore_memory_from(hold);
		// set directory new size
		add_FD_length(0, FSConst.DIR_SLOT_SIZE);
	}
	
	public void destroy(String filename)
	{
		int fileIndex = search_file_by_filename(filename);
		int fdIndex = get_FD_from_file_index(fileIndex);
		int oftIndex = get_oft_index_from_FD(fdIndex);
		ArrayList<Byte> hold = new ArrayList<>();
		
		copy_memory_to(hold);
		free_FD(fdIndex);
		mainMem.clear();
		lseek(0, fileIndex);
		read(0, mainMem, FSConst.DIR_SLOT_SIZE);
		
		for(int i = 0; i < 8; i++)
			mainMem.set(i, (byte)-1);
		
		lseek(0, fileIndex);
		write(0, mainMem, FSConst.DIR_SLOT_SIZE);
		
		if(oftIndex > 0)
			close(oftIndex);
		
		restore_memory_from(hold);
	}
	
	public int open(String filename)
	{
		int fileIndex = search_file_by_filename(filename);
		int fdIndex = get_FD_from_file_index(fileIndex);
		int oftIndex = get_oft();
		int block = get_FD_block_n(fdIndex, 0);
		
		// Check it the filename is not already open
		for(int i = 1; i <= 3; i++)
			if(oftVar[i][1] == fdIndex)
				return i;
		
		if(oftIndex < 0)
		{
			System.out.println("Can't open the file, OFS is full");
			return -1;
		}
		
		// Assign a block if none was assigned yet
		if(block <= 0)
		{
			block = assign_block();
			add_FD_block(fdIndex, block);
		}
		
		oft[oftIndex] = d.read_block(block);
		oftVar[oftIndex][0] = 0;
		oftVar[oftIndex][1] = fdIndex;
		oftVar[oftIndex][2] = get_FD_length(fdIndex);
		
		return oftIndex;
	}
	
	public int close(int oftIndex)
	{
		oft[oftIndex] = null;
		oftVar[oftIndex][0] = 0;
		oftVar[oftIndex][1] = 0;
		oftVar[oftIndex][2] = 0;
		
		return oftIndex;
	}
	
	// Read n bytes from the chosen OFT buffer to the main memory
	public int read(int oftIndex, ArrayList<Byte> memArea, int byteCount)
	{
		int index = 0, decount = byteCount;
		
		get_right_block_to_oft(oftIndex);
		for(int i = oftVar[oftIndex][0]; i < oftVar[oftIndex][2]; i++)
		{
			if(decount <= 0)
				break;
			
			index = i%FSConst.BLOCK_SIZE;
			if(index > 0)
			{
				memArea.add(oft[oftIndex][index]);
				oftVar[oftIndex][0]++;
				decount--;
			}
			else
			{
				fetch_next_block(oftIndex);
				memArea.add(oft[oftIndex][index]);
				oftVar[oftIndex][0]++;
				decount--;
			}
		}
		return byteCount-decount;
	}
	
	// Write n bytes from main memory to the chosen OFT buffer and save it in the disk
	public int write(int oftIndex, ArrayList<Byte> memArea, int byteCount)
	{
		int written = 0, index = 0, decount = byteCount;

		get_right_block_to_oft(oftIndex);
		for (int i = oftVar[oftIndex][0]; i < FSConst.MAX_FILE_SIZE; i++)
		{
			if (decount <= 0 || byteCount-decount >= memArea.size())
				break;

			index = i % FSConst.BLOCK_SIZE;
			if (index > 0) 
			{
				oft[oftIndex][index] = memArea.get(byteCount-decount);
				oftVar[oftIndex][0]++;
				decount--;
			} 
			else if((oftVar[oftIndex][2] + (byteCount - decount)) <= FSConst.MAX_FILE_SIZE)
			{
				if(get_FD_block_n(oftVar[oftIndex][1], i/FSConst.BLOCK_SIZE) <= 0)
					add_FD_block(oftVar[oftIndex][1], assign_block());
				
				fetch_next_block(oftIndex);
				oft[oftIndex][index] = memArea.get(byteCount-decount);
				oftVar[oftIndex][0]++;
				decount--;
			}
		}
		written = oftVar[oftIndex][0] - oftVar[oftIndex][2];
		if(written > 0)
		{
			oftVar[oftIndex][2] += written;
			add_FD_length(oftVar[oftIndex][1], written);
		}
		save_current_block_to_disk(oftIndex);
		return byteCount - decount;
	}

	// Reposition the cursor in the chosen OFT
	public int lseek(int oftIndex, int pos) 
	{
		int p = pos;
		if(pos > oftVar[oftIndex][2])
			p = oftVar[oftIndex][2];
		
		int block = p / FSConst.BLOCK_SIZE;
		
		if (block == get_current_block(oftIndex) ) 
		{
			oftVar[oftIndex][0] = p;
		} 
		else 
		{
			fetch_block(oftIndex, block);
			oftVar[oftIndex][0] = p;
		}
		return p;
	}
	
	public void directory()
	{
		ArrayList<Byte> cache = new ArrayList<>();
		
		lseek(0, 0);
		while(read(0, cache, FSConst.DIR_SLOT_SIZE) > 0)
		{
			if(cache.get(0) > 0)
			{
				for(int i = 0; i < 4; i++)
					if(cache.get(i) != 0)
						System.out.print((char)((byte)cache.get(i)));
				
				System.out.print(" ");
			}
			cache.clear();
		}
	}
	
	// Copy content of main memory to the arary list l
	private void copy_memory_to(ArrayList<Byte> l)
	{
		for(int i =0; i < mainMem.size(); i++)
			l.add(mainMem.get(i));
	}
	
	// Restore memory from array list l
	private void restore_memory_from(ArrayList<Byte> l) 
	{
		mainMem.clear();
		for (int i = 0; i < l.size(); i++)
			mainMem.add(l.get(i));
	}
	
	// Get the first free OFT slot
	private int get_oft()
	{
		for(int i = 1; i <= 3; i++)
			if(oftVar[i][1] <= 0)
				return i;
		return -1;
	}
	
	// Get the OFT index from the file descriptor
	private int get_oft_index_from_FD(int fdIndex)
	{
		for(int i = 1; i <= 3; i++)
			if(oftVar[i][1] == fdIndex)
				return i;
		return -1;
	}
	
	// Fetch the right block in OFT buffer
	private void get_right_block_to_oft(int oftIndex) 
	{
		int currBlock = get_current_block(oftIndex);
		int prevBlockIndex = oftVar[oftIndex][0]/FSConst.BLOCK_SIZE-1;
		check_valid_block(currBlock);
		
		if(currBlock > 0)
			oft[oftIndex] = d.read_block(currBlock);
		else if(prevBlockIndex >= 0)
			oft[oftIndex] = d.read_block(get_FD_block_n(oftVar[oftIndex][1], prevBlockIndex));
	}
	
	// Save the OFT buffer in the correct block
	private void save_current_block_to_disk(int oftIndex)
	{
		int currBlock = get_current_block(oftIndex);
		int prevBlockIndex = oftVar[oftIndex][0]/FSConst.BLOCK_SIZE-1;
		check_valid_block(currBlock);
		
		if(currBlock > 0)
			d.write_block(currBlock, oft[oftIndex]);
		else if(prevBlockIndex >= 0)
			d.write_block(get_FD_block_n(oftVar[oftIndex][1], prevBlockIndex), oft[oftIndex]);
	}
	
	// Get current Block
	private int get_current_block(int oftIndex) 
	{
		int block, currBlock = (oftVar[oftIndex][0]-1) / FSConst.BLOCK_SIZE;
		
		block = get_FD_block_n(oftVar[oftIndex][1], currBlock);
		return block;
	}
	
	// Fetch in the block n in the OFT buffer
	private void fetch_block(int oftIndex, int n)
	{
		int block;
		
		if(n < 0 || n > 2)
			throw new IndexOutOfBoundsException("Invalid block " + n + "in fetch_block(int oftIndex, int n).");
		
		block = get_FD_block_n(oftVar[oftIndex][1], n);
		check_valid_block(block);
		save_current_block_to_disk(oftIndex);
		oft[oftIndex] = d.read_block(block);
	}
	
	// Fetch in the OFT buffer, the next block that is supposed to be read
	private void fetch_next_block(int oftIndex)
	{
		int block, blockN = oftVar[oftIndex][0]/FSConst.BLOCK_SIZE;
		
		if(blockN < 0 || blockN > 2)
			throw new IndexOutOfBoundsException("Invalid block " + blockN + "in fetch_next_block(int oftIndex).");
		
		block = get_FD_block_n(oftVar[oftIndex][1], blockN);
		check_valid_block(block);
		
		if(blockN-1 >= 0)
			d.write_block(get_FD_block_n(oftVar[oftIndex][1], blockN-1), oft[oftIndex]);
		
		oft[oftIndex] = d.read_block(block);
	}
	
	// Return index of the first available file slot
	private int get_available_dirSlot() 
	{
		if(oftVar[0][2] == 0)
			oftVar[0][2] = FSConst.DIR_SLOT_SIZE;
		
		lseek(0, 0);
		mainMem.clear();
		while(read(0, mainMem, FSConst.DIR_SLOT_SIZE) > 0)
		{
			if(mainMem.get(0) < 0)
				return oftVar[0][0]-FSConst.DIR_SLOT_SIZE;
			mainMem.clear();
		}
		if((oftVar[0][2]) % FSConst.BLOCK_SIZE == 0)
			add_FD_block(0, assign_block());
		
		if(oftVar[0][2] < FSConst.MAX_FILE_SIZE)
			oftVar[0][2] += FSConst.DIR_SLOT_SIZE;
		
		read(0, mainMem, FSConst.DIR_SLOT_SIZE);
		
		return oftVar[0][0]-FSConst.DIR_SLOT_SIZE;
		
	}
	
	// Get FD index from file index
	private int get_FD_from_file_index(int fileIndex)
	{
		ArrayList<Byte> cache = new ArrayList<>();
		PackableMemory b = new PackableMemory(4);
		lseek(0, fileIndex + 4);
		read(0, cache, 4);
		
		for(int i = 0; i < 4; i++)
			b.mem[i] = cache.get(i);
		
		return b.unpack(0);
	}

	// Search for a file in the file descriptors
	protected int search_file_by_filename(String filename)
	{
		check_valid_filename(filename);
		ArrayList<Byte> cache = new ArrayList<>();
		
		lseek(0, 0);
		cache.clear();
		while(read(0, cache, FSConst.DIR_SLOT_SIZE) > 0)
		{
			for(int i = 0; i < filename.length(); i++)
				if(compare_filename(cache, filename))
					return oftVar[0][0]-8;
			
			cache.clear();
		}
		return -1;
	}
	
	// Subprocess to search for a filename
	private boolean compare_filename(ArrayList<Byte> n1, String n2)
	{
		for (int i = 0; i < n2.length(); i++)
			if (n1.get(i) != (byte)n2.charAt(i))
				return false;
		
		return true;
	}

	// Return the length of the file descriptor n
	private int get_FD_length(int fdIndex)
	{
		int[] fdBlockIndex = get_FD_blockIndex(fdIndex);
		PackableMemory b = new PackableMemory(FSConst.BLOCK_SIZE);
		
		b.mem = d.read_block(fdBlockIndex[0]);
		return b.unpack(fdBlockIndex[1]);
	}
	
	// get FD block 0, 1, or 2 (depends of value of n)
	private int get_FD_block_n(int fdIndex, int n)
	{
		int[] fdBlockIndex = get_FD_blockIndex(fdIndex);
		
		PackableMemory b = new PackableMemory(FSConst.BLOCK_SIZE);
		b.mem = d.read_block(fdBlockIndex[0]);
		
		return b.unpack( fdBlockIndex[1] + (FSConst.STEP_SIZE * (n+1)));
	}
	
	// Sum the length of the file descriptor n with the value length
	private void add_FD_length(int fdIndex, int length)
	{
		int[] fdBlockIndex = get_FD_blockIndex(fdIndex);
		PackableMemory b = new PackableMemory(FSConst.BLOCK_SIZE);
		
		b.mem = d.read_block(fdBlockIndex[0]);
		b.pack(b.unpack( fdBlockIndex[1])+length, fdBlockIndex[1]);
		d.write_block(fdBlockIndex[0], b.mem);
	}
	
	// Add a block in the file descriptor and set the bitmap
	private void add_FD_block(int fdIndex, int block)
	{
		if(!add_FD_block_subProcess(fdIndex, block))
			throw new IndexOutOfBoundsException("Maximum block reached for file descriptor " + fdIndex + ".");
	}
	
	
	// Subprocess to add a block in the file descriptor and set the bitmap
	private boolean add_FD_block_subProcess(int fdIndex, int block)
	{
		int[] fdBlockIndex = get_FD_blockIndex(fdIndex);
		int step;
		PackableMemory b = new PackableMemory(FSConst.BLOCK_SIZE);
		
		b.mem = d.read_block(fdBlockIndex[0]);
		
		for(int i = 0; i < 3; i++)
		{
			step = FSConst.STEP_SIZE * (i+1);
			if(b.unpack(fdBlockIndex[1] + step) <= 0)
			{
				b.pack(block, fdBlockIndex[1] + step);
				d.write_block(fdBlockIndex[0], b.mem);
				set_bitmap(block);
				return true;
			}
		}
		return false;
	}
	
	// Initialize the file descriptor n
	private void free_FD(int fdIndex)
	{
		int[] fdBlockIndex = get_FD_blockIndex(fdIndex);
		int block;
		PackableMemory b = new PackableMemory(FSConst.BLOCK_SIZE);
		
		b.mem = d.read_block(fdBlockIndex[0]);
		for(int i = fdBlockIndex[1]; i < fdBlockIndex[1]+FSConst.FD_SLOT_SIZE; i+=FSConst.STEP_SIZE)
		{
			block = b.unpack(i);
			if(i > fdBlockIndex[1] && block > 0)
				free_block(block);
			b.pack(-1, i);
		}
		d.write_block(fdBlockIndex[0], b.mem);
	}
	
	// Process the file descriptor index from its block index
	private int get_FD_index(int fdIndex[])
	{
		return (fdIndex[0]-1)*4 + fdIndex[1]/FSConst.FD_SLOT_SIZE;
	}
	
	// Process the file descriptor block index index from its index
	private int[] get_FD_blockIndex(int fdIndex)
	{
		int block = fdIndex/4, i = fdIndex-block*4;
		
		int[] fdBlockIndex = {block+1, i*FSConst.FD_SLOT_SIZE};
		return fdBlockIndex;
	}
	
	// Return the first file descriptor available to use (file descriptor n#0 is reserved for the directory)
	private int[] available_FD()
	{
		PackableMemory b = new PackableMemory(FSConst.BLOCK_SIZE);
		int[] fdIndex = new int[2];
		
		// Traverse disk block (0 contain the bitmap, thus it start at block 1)
		for(int i = 1; i <= FSConst.FD_BLOCKS; i++)
		{
			b.mem = d.read_block(i);
			for(int j = 0; j < FSConst.BLOCK_SIZE; j+=FSConst.FD_SLOT_SIZE)
				if((int)(b.mem[j]) < 0)
				{
					fdIndex[0] = i;
					fdIndex[1] = j;
					Initialize_FD(fdIndex);
					return fdIndex;
				}
		}
		throw new RuntimeException("No file descriptor available.");
	}
	
	// set a new empty file descriptor
	private void Initialize_FD(int fdIndex[])
	{
		PackableMemory b = new PackableMemory(FSConst.BLOCK_SIZE);
		
		b.mem = d.read_block(fdIndex[0]);
		for(int i = fdIndex[1]; i < FSConst.FD_SLOT_SIZE+fdIndex[1]; i+=FSConst.STEP_SIZE)
			b.pack(0, i);
		d.write_block(fdIndex[0], b.mem);
	}
	
	// Initialize the block n
	private void free_block(int n)
	{
		unset_bitmap(n);
		init_block(n);
	}
	
	// Assign a free block and set the associated bitmap
	private int assign_block() 
	{
		int block = available_block();

		set_bitmap(block);
		return block;
	}

	// Check bitmap and return first available block, do not set bimap
	private int available_block()
	{
		PackableMemory b = new PackableMemory(FSConst.BLOCK_SIZE);
		int m ,index;
		b.mem = d.read_block(0);
		
		// Traverse bitmap bit 7 to bit 63 (0: bitmap, 1-6: File descriptors)
		for(int i = FSConst.FD_BLOCKS+1; i < FSConst.BLOCK_SIZE; i++)
		{
			m = i % 8;
			index = i / 8;
			if(((int)b.mem[index] & FSConst.MASK[m]) == 0)
				return i;
		}
		throw new RuntimeException("No disk block available.");
	}
	
	// Set bitmap bitNumb to used (bit = 1)
	private void set_bitmap(int bitNumb)
	{
		check_valid_bitmap_value(bitNumb);
		PackableMemory b = new PackableMemory(FSConst.BLOCK_SIZE);
		int m = bitNumb % 8;
		int index = bitNumb / 8;
		
		b.mem = d.read_block(0);
		b.mem[index] = (byte) ((int)b.mem[index] | FSConst.MASK[m]);
		d.write_block(0, b.mem);
	}
	
	// Set bitmap bitNumb to not used (bit = 0)
	private void unset_bitmap(int bitNumb)
	{
		check_valid_bitmap_value(bitNumb);
		PackableMemory b = new PackableMemory(FSConst.BLOCK_SIZE);
		int m = bitNumb % 8;
		int index = bitNumb / 8;
		
		b.mem = d.read_block(0);
		b.mem[index] = (byte) ((int)b.mem[index] & ~FSConst.MASK[m]);
		d.write_block(0, b.mem);
	}
	
	// Errors handling
	private void check_valid_bitmap_value(int n)
	{
		if(n < 0 || n > 63)
			throw new IndexOutOfBoundsException("Invalid bitmap value: " + n);
	}
	
	private void check_valid_block(int n)
	{
		if(n < 0 || n > 63)
			throw new IndexOutOfBoundsException("Invalid block value: " + n);
	}
	
	private void check_valid_filename(String filename)
	{
		if(filename.length() > 4)
			throw new IndexOutOfBoundsException("Filename is too long: " + filename.length());
	}
	
	// Initializer
	
	protected void init_all()
	{
		oft = new byte[4][64];
		oftVar = new int[4][3];
		mainMem = new ArrayList<>();
		d = new Disk();
		
		init_bitmap();
		init_blocks();
		init_dir();
		init_oft_dir();
		mainMem.clear();
	}

	// Initialize block 0 - 6 as taken in the bitmap
	private void init_bitmap() 
	{
		for (int i = 0; i <= 6; i++)
			set_bitmap(i);
	}

	// Initialize blocks 1-63 to -1
	private void init_blocks() {
		for (int i = 1; i < FSConst.MAX_BLOCKS; i++) 
			init_block(i);
	}
	
	private void init_block(int n)
	{
		PackableMemory b = new PackableMemory(FSConst.BLOCK_SIZE);
		b.mem = d.read_block(n);

		for (int j = 0; j < FSConst.BLOCK_SIZE; j += FSConst.STEP_SIZE)
			b.pack(-1, j);

		d.write_block(n, b.mem);
	}

	// Initialize the file descriptor of the directory to be all 0s
	private void init_dir() 
	{
		PackableMemory b = new PackableMemory(FSConst.BLOCK_SIZE);
		b.mem = d.read_block(1);

		for (int j = 0; j < 4 * FSConst.STEP_SIZE; j += FSConst.STEP_SIZE)
			b.pack(0, j);

		d.write_block(1, b.mem);
	}
	
	// Initialize oft 0 to contain the directory
	private void init_oft_dir()
	{
		int block = assign_block();
		add_FD_block(0, block);
		oft[0] = d.read_block(block);
		for(int i = 0; i < FSConst.OFT_VARS; i++)
			oftVar[0][i] = 0;
	}
	
	// Load directory to the OFT
	protected void load_dir_to_oft()
	{
		int block = get_FD_block_n(0, 0);
		
		oft[0] = d.read_block(block);
		oftVar[0][0] = 0;
		oftVar[0][1] = 0;
		oftVar[0][2] = get_FD_length(0);
	}
}
