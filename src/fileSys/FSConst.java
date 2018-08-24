package fileSys;

public class FSConst 
{
	// All size in block number
	public static final int MAX_BLOCKS = 64;
	public static final int FD_BLOCKS = 6;
	public static final int MAX_FILE_BLOCKS = 3;
	
	// All size in bytes
	public static final int MAX_FILE_SIZE = 192;
	public static final int BLOCK_SIZE = 64;
	public static final int DIR_SLOT_SIZE = 8;
	public static final int STEP_SIZE = 4;
	public static final int FD_SLOT_SIZE = 16;
	
	// OFT
	public static final int OFT_SIZE = 4;
	public static final int OFT_VARS = 3;
	
	public static final int[] MASK = {0x80, 0x40, 0x20, 0x10, 0x08, 0x04, 0x02, 0x01};
	
}
