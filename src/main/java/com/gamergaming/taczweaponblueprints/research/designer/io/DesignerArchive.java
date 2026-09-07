package com.gamergaming.taczweaponblueprints.research.designer.io;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Bounded, reproducible archives. Callers consume bytes; no archive paths are extracted. */
public final class DesignerArchive {
    public static final int MAX_BYTES=96*1024*1024, MAX_ENTRY=32*1024*1024, MAX_FILES=16_384;
    private DesignerArchive(){}
    public static void path(String name){
        if(name.isEmpty()||name.length()>512||name.startsWith("/")||name.contains("\\")||name.contains(":")||name.chars().anyMatch(Character::isISOControl)||Arrays.stream(name.split("/",-1)).anyMatch(p->p.isEmpty()||p.equals(".")||p.equals("..")))throw new IllegalArgumentException("Unsafe archive path: "+name);
    }
    public static byte[] write(Map<String,byte[]> files)throws IOException{
        if(files.isEmpty()||files.size()>MAX_FILES)throw new IOException("Too many archive entries");long total=0;var bytes=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(bytes)){
            for(var item:new TreeMap<>(files).entrySet()){path(item.getKey());var value=item.getValue();total+=value.length;if(value.length>MAX_ENTRY||total>MAX_BYTES)throw new IOException("Archive exceeds size limit");
                var e=new ZipEntry(item.getKey());e.setTimeLocal(java.time.LocalDateTime.of(1980,1,1,0,0));e.setMethod(ZipEntry.STORED);e.setSize(value.length);e.setCompressedSize(value.length);var crc=new CRC32();crc.update(value);e.setCrc(crc.getValue());zip.putNextEntry(e);zip.write(value);zip.closeEntry();
                if(bytes.size()>MAX_BYTES)throw new IOException("Archive exceeds size limit");
            }
        }if(bytes.size()>MAX_BYTES)throw new IOException("Archive exceeds size limit");return bytes.toByteArray();
    }
    public static Map<String,byte[]> read(byte[] archive)throws IOException{
        if(archive.length>MAX_BYTES)throw new IOException("Archive exceeds size limit");var directory=directory(archive);var files=new TreeMap<String,byte[]>();long total=0;
        try(var zip=new ZipInputStream(new ByteArrayInputStream(archive))){ZipEntry e;byte[] block=new byte[8192];while((e=zip.getNextEntry())!=null){path(e.getName());if(e.isDirectory()||files.containsKey(e.getName())||files.size()>=MAX_FILES)throw new IOException("Duplicate, directory or excessive archive entry");
                var out=new ByteArrayOutputStream();int count;while((count=zip.read(block))!=-1){total+=count;if(total>MAX_BYTES||out.size()+count>MAX_ENTRY)throw new IOException("Expanded archive exceeds size limit");out.write(block,0,count);}zip.closeEntry();files.put(e.getName(),out.toByteArray());}
        }
        if(files.isEmpty()||!files.keySet().equals(directory))throw new IOException("Archive directory differs from entries");return Collections.unmodifiableMap(files);
    }
    private static Set<String> directory(byte[] bytes)throws IOException{
        var b=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);int end=-1;
        for(int p=bytes.length-22;p>=Math.max(0,bytes.length-65557);p--)if(b.getInt(p)==0x06054b50&&p+22+Short.toUnsignedInt(b.getShort(p+20))==bytes.length){end=p;break;}
        if(end<0||b.getShort(end+4)!=0||b.getShort(end+6)!=0)throw new IOException("Incomplete or multi-disk ZIP");
        int count=Short.toUnsignedInt(b.getShort(end+10));long size=Integer.toUnsignedLong(b.getInt(end+12)),start=Integer.toUnsignedLong(b.getInt(end+16));
        if(count<1||count>MAX_FILES||Short.toUnsignedInt(b.getShort(end+8))!=count||start+size!=end)throw new IOException("Invalid ZIP directory");
        var names=new HashSet<String>();var offsets=new HashSet<Long>();long expanded=0;int p=(int)start;
        for(int i=0;i<count;i++){
            if(p<0||p+46>end||b.getInt(p)!=0x02014b50)throw new IOException("Invalid ZIP entry directory");
            int n=Short.toUnsignedInt(b.getShort(p+28)),extra=Short.toUnsignedInt(b.getShort(p+30)),comment=Short.toUnsignedInt(b.getShort(p+32));
            long length=Integer.toUnsignedLong(b.getInt(p+24)),offset=Integer.toUnsignedLong(b.getInt(p+42));expanded+=length;
            if(n<1||p+46+n+extra+comment>end||length>MAX_ENTRY||expanded>MAX_BYTES||!offsets.add(offset)||offset+30>start||b.getInt((int)offset)!=0x04034b50||(b.getShort(p+8)&1)!=0||b.getShort(p+34)!=0)throw new IOException("Invalid, encrypted or oversized ZIP entry");
            int local=(int)offset,localName=Short.toUnsignedInt(b.getShort(local+26)),localExtra=Short.toUnsignedInt(b.getShort(local+28));
            if(local+30L+localName+localExtra>start||localName!=n||!Arrays.equals(bytes,local+30,local+30+localName,bytes,p+46,p+46+n)||b.getShort(local+6)!=b.getShort(p+8)||b.getShort(local+8)!=b.getShort(p+10))throw new IOException("ZIP local header differs from directory");
            if((b.getShort(p+8)&8)==0&&(b.getInt(local+14)!=b.getInt(p+16)||b.getInt(local+18)!=b.getInt(p+20)||b.getInt(local+22)!=b.getInt(p+24)))throw new IOException("ZIP local sizes or checksum differ from directory");
            int unixMode=(b.getInt(p+38)>>>16)&0xf000;if(unixMode==0xa000)throw new IOException("Symlink ZIP entries are unsupported");
            String name;try{name=java.nio.charset.StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes,p+46,n)).toString();}catch(java.nio.charset.CharacterCodingException invalid){throw new IOException("Invalid entry name",invalid);}
            path(name);if(!names.add(name))throw new IOException("Duplicate ZIP directory entry");p+=46+n+extra+comment;
        }if(p!=end)throw new IOException("Invalid ZIP directory length");return names;
    }
}
