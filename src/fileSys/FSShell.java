package fileSys;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

import fileSys.FSConst;

public class FSShell extends Interface{

	private static FSShell shell;
	
	public static void main(String[] args)
	{
		shell = new FSShell();
		
		if(args.length > 0)
		{
			try {
				shell.file_system_shell((String)args[0]);
			} 
			catch (Exception e) {
				e.printStackTrace();
			}
		}
		else
			shell.file_system_shell();
	}
	
	public FSShell()
	{
		super();
	}
	
	public void file_system_shell(String filePath) throws Exception
	{
		String input;
		File inputFile = new File(filePath);
		Scanner in = new Scanner(inputFile);
		
		while (in.hasNext()) 
		{
			input = in.nextLine();
			fetch_command(input);
		}
		
		in.close();
	}
	
	public void file_system_shell()
	{
		String input;
		Scanner in = new Scanner(System.in);
		
		do
		{
			System.out.print("$ ");
			input = in.nextLine();
		} while(!fetch_command(input));
		
		in.close();
	}
	
	public boolean fetch_command(String input)
	{
		String[] command;
		
		command = null;
		command = input.split("\\s+");
		return execute_command(command);
	}
	
	private boolean execute_command(String[] command)
	{
		switch(command[0])
		{
			case "cr":
				if(command.length == 2)
					sh_create(command[1]);
				return false;
			
			case "de":
				if(command.length == 2)
					sh_destroy(command[1]);
				return false;
				
			case "op":
				if(command.length == 2)
					sh_open(command[1]);
				return false;
			
			case "cl":
				if(command.length == 2)
					sh_close(Integer.parseInt(command[1]));
				return false;
			
			case "rd":
				if(command.length == 3)
					sh_read(Integer.parseInt(command[1]), Integer.parseInt(command[2]));
				return false;
			
			case "wr":
				if(command.length == 4)
					sh_write(Integer.parseInt(command[1]), (byte)command[2].charAt(0), Integer.parseInt(command[3]));
				return false;
			
			case "sk":
				if(command.length == 3)
					sh_seek(Integer.parseInt(command[1]), Integer.parseInt(command[2]));
				return false;
				
			case "dr":
				directory();
				System.out.println();
				return false;
				
			case "in":
				if(command.length == 1)
					disk_init();
				else
					disk_init(command[1]);
				return false;
				
			case "sv":
				if(command.length == 1)
					System.out.println("error");
				else
					save_disk(command[1]);
				return false;
				
			case "q":
				return true;
				
			default:
				System.out.println();
				return false;
		}
	}
	
	public void sh_create(String filename)
	{
		int fileIndex = search_file_by_filename(filename);
		
		if(fileIndex < 0)
		{
			create(filename);
			System.out.println(filename + " created");
		}
		else
			System.out.println("error");
	}
	
	public void sh_destroy(String filename)
	{
		int fileIndex = search_file_by_filename(filename);
		
		if(fileIndex >= 0)
		{
			destroy(filename);
			System.out.println(filename + " destroyed");
		}
		else
			System.out.println("error");
	}
	
	public void sh_open(String filename)
	{
		int fileIndex = search_file_by_filename(filename);
		
		if(fileIndex >= 0)
			System.out.println(filename + " opened " + open(filename));
		else
			System.out.println("error");
	}
	
	public void sh_close(int oftIndex)
	{
		System.out.println(close(oftIndex) + " closed");
	}
	
	public void sh_read(int oftIndex, int byteCount)
	{
		ArrayList<Byte> cache = new ArrayList<>();
		
		if(oftIndex > 0 && oftIndex < 4 && oftVar[oftIndex][1] > 0 )
		{
			read(oftIndex, cache, byteCount);
			for(int i = 0; i< cache.size(); i++)
				System.out.print((char)(int)(cache.get(i)));
			System.out.println();
		}
		else
			System.out.println("error");
	}
	
	public void sh_write(int oftIndex, byte charToWr ,int byteCount)
	{
		ArrayList<Byte> cache = new ArrayList<>();
		
		if(oftIndex > 0 && oftIndex < 4 && oftVar[oftIndex][1] > 0)
		{
			for(int i = 0; i < byteCount; i++)
				cache.add(charToWr);
			
			System.out.println(write(oftIndex, cache, byteCount) + " bytes written");
		}
		else
			System.out.println("error");
	}
	
	public void sh_seek(int oftIndex, int pos)
	{
		if(oftIndex > 0 && oftIndex < 4 && oftVar[oftIndex][1] > 0 && pos >= 0)
			System.out.println("position is " + lseek(oftIndex, pos));
		else
			System.out.println("error");
	}
	
	public void disk_init()
	{
		init_all();
		System.out.println("disk initialized");
	}
	
	public void disk_init(String filename)
	{
		File temp = new File(filename);
		try {
			if( temp.exists())
			{
				load_from_file(filename);
				System.out.println("disk restored");
			}
			else
				System.out.println(filename + "not found.");
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void save_disk(String filename)
	{
		for(int i = 0; i <= 3; i++)
			close(i);
		
		try {
			save_in_file(filename);
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
		
		System.out.println("disk saved");
	}
	
	public void save_in_file(String filename) throws IOException
	{
		BufferedWriter txtOutput = new BufferedWriter(new FileWriter(filename));
		
		for(int i =0; i < FSConst.MAX_BLOCKS; i++)
		{
			for (int j = 0; j < FSConst.BLOCK_SIZE; j++) 
				txtOutput.write(d.ldisk[i][j] + " ");
			
			txtOutput.newLine();
		}
		txtOutput.flush();
		txtOutput.close();
	}
	
	public void load_from_file(String filename) throws IOException
	{
		BufferedReader txtInput = new BufferedReader(new FileReader(filename));
		String line_input;
		String[] block_inputs;
		
		for(int i =0; i < FSConst.MAX_BLOCKS; i++)
	    {
	    	line_input = txtInput.readLine();
	        block_inputs = line_input.split("\\s+");
	        
			for (int j = 0; j < FSConst.BLOCK_SIZE; j++) 
				d.ldisk[i][j] = (byte) Integer.parseInt(block_inputs[j]);
	    }
	    // initialize the directory to oft 0
	    load_dir_to_oft();
		txtInput.close();
	}

}
