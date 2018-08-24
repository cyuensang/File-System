package fileSys;

public class Disk {

	protected byte[][] ldisk = null;
	
	public Disk() 
	{
		ldisk = new byte[64][64];
	}
	
	public byte[] read_block(int i)
	{
		return ldisk[i].clone();
	}
	
	public void write_block(int i, byte[] p)
	{
		ldisk[i] = p.clone();
	}

}
