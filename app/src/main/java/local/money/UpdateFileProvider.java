package local.money;
import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
public final class UpdateFileProvider extends ContentProvider {
 @Override public boolean onCreate(){return true;}
 private void check(Uri u){if(!("content://"+getContext().getPackageName()+".updates/app.apk").equals(u.toString()))throw new IllegalArgumentException("Unknown file");}
 @Override public String getType(Uri u){check(u);return "application/vnd.android.package-archive";}
 @Override public ParcelFileDescriptor openFile(Uri u,String mode)throws FileNotFoundException{check(u);if(!"r".equals(mode))throw new FileNotFoundException("Read only");return ParcelFileDescriptor.open(AppUpdates.file(getContext()),ParcelFileDescriptor.MODE_READ_ONLY);}
 @Override public Cursor query(Uri u,String[] projection,String selection,String[] args,String sort){check(u);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor c=new MatrixCursor(columns);Object[] values=new Object[columns.length];for(int i=0;i<columns.length;i++)values[i]=OpenableColumns.DISPLAY_NAME.equals(columns[i])?"app.apk":OpenableColumns.SIZE.equals(columns[i])?AppUpdates.file(getContext()).length():null;c.addRow(values);return c;}
 @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
 @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
 @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
