package ir.meelano.manager.ui;

import android.app.Activity;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** System print (printer picker) for a PDF file — no libraries needed. */
public final class PrintKit {
    private PrintKit() { }

    public static void printPdf(final Activity a, final File pdf, final String jobName) {
        if (a == null || pdf == null || !pdf.exists()) return;
        try {
            PrintManager pm = (PrintManager) a.getSystemService(Activity.PRINT_SERVICE);
            if (pm == null) return;
            PrintDocumentAdapter adapter = new PrintDocumentAdapter() {
                @Override
                public void onLayout(PrintAttributes oldA, PrintAttributes newA,
                                     CancellationSignal cancel, LayoutResultCallback cb, android.os.Bundle extras) {
                    if (cancel.isCanceled()) {
                        cb.onLayoutCancelled();
                        return;
                    }
                    PrintDocumentInfo info = new PrintDocumentInfo.Builder("report.pdf")
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                            .build();
                    cb.onLayoutFinished(info, !newA.equals(oldA));
                }

                @Override
                public void onWrite(PageRange[] pages, ParcelFileDescriptor dest,
                                    CancellationSignal cancel, WriteResultCallback cb) {
                    InputStream in = null;
                    OutputStream out = null;
                    try {
                        in = new FileInputStream(pdf);
                        out = new FileOutputStream(dest.getFileDescriptor());
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = in.read(buf)) >= 0 && !cancel.isCanceled()) out.write(buf, 0, n);
                        if (cancel.isCanceled()) cb.onWriteCancelled();
                        else cb.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
                    } catch (Exception e) {
                        cb.onWriteFailed(e == null ? "خطا" : String.valueOf(e.getMessage()));
                    } finally {
                        try {
                            if (in != null) in.close();
                        } catch (Exception ignored) { }
                        try {
                            if (out != null) out.close();
                        } catch (Exception ignored) { }
                    }
                }
            };
            pm.print(jobName == null || jobName.isEmpty() ? "گزارش میلانو" : jobName, adapter, null);
        } catch (Exception ignored) { }
    }
}
