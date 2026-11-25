"""
LibreOffice UNO helper: connects to a running headless soffice instance,
opens a .docx file, updates all fields and DocumentIndexes (TOC, List of Figures,
List of Tables, SEQ fields), and exports the .pdf directly via writer_pdf_Export.

Run with LibreOffice's bundled Python:
    "C:\\Program Files\\LibreOffice\\program\\python.exe" docs\\build\\lo_update_and_export.py <input.docx> <output.pdf> [port]
"""
import os
import sys
import time
import urllib.parse
import uno
from com.sun.star.beans import PropertyValue


def to_file_url(path: str) -> str:
    abs_path = os.path.abspath(path).replace("\\", "/")
    if not abs_path.startswith("/"):
        abs_path = "/" + abs_path
    return "file://" + urllib.parse.quote(abs_path, safe="/:")


def make_prop(name: str, value):
    p = PropertyValue()
    p.Name = name
    p.Value = value
    return p


def connect(port: int, retries: int = 40):
    local_ctx = uno.getComponentContext()
    resolver = local_ctx.ServiceManager.createInstanceWithContext(
        "com.sun.star.bridge.UnoUrlResolver", local_ctx
    )
    url = f"uno:socket,host=127.0.0.1,port={port};urp;StarOffice.ComponentContext"
    last_err = None
    for _ in range(retries):
        try:
            ctx = resolver.resolve(url)
            smgr = ctx.ServiceManager
            desktop = smgr.createInstanceWithContext("com.sun.star.frame.Desktop", ctx)
            return desktop
        except Exception as e:
            last_err = e
            time.sleep(0.5)
    raise RuntimeError(f"Failed to connect to LibreOffice UNO on port {port}: {last_err}")


def main():
    if len(sys.argv) < 3:
        print("Usage: lo_update_and_export.py <input.docx> <output.pdf> [port]", file=sys.stderr)
        sys.exit(2)

    in_docx = sys.argv[1]
    out_pdf = sys.argv[2]
    port = int(sys.argv[3]) if len(sys.argv) >= 4 else 2002

    desktop = connect(port)
    in_url = to_file_url(in_docx)
    out_url = to_file_url(out_pdf)

    load_props = (
        make_prop("Hidden", True),
        make_prop("ReadOnly", False),
    )
    doc = desktop.loadComponentFromURL(in_url, "_blank", 0, load_props)
    if doc is None:
        raise RuntimeError(f"LibreOffice could not open {in_docx}")

    try:
        # Refresh text fields (SEQ, PAGE, etc.)
        if hasattr(doc, "getTextFields"):
            doc.getTextFields().refresh()
        doc.refresh()

        # Update all DocumentIndexes (TOC, List of Figures, List of Tables) twice
        # so pagination stabilizes if an index expands across pages.
        for _pass in range(2):
            indexes = doc.getDocumentIndexes()
            for i in range(indexes.getCount()):
                idx = indexes.getByIndex(i)
                idx.update()
            if hasattr(doc, "getTextFields"):
                doc.getTextFields().refresh()
            doc.refresh()

        # Export directly from the updated Writer document to PDF (fonts embedded)
        export_props = (
            make_prop("FilterName", "writer_pdf_Export"),
            make_prop("Overwrite", True),
            make_prop("EmbedStandardFonts", True),
            make_prop("UseLosslessCompression", False),
            make_prop("Quality", 90),
            make_prop("ReduceImageResolution", False),
            make_prop("ExportBookmarks", True),
        )
        doc.storeToURL(out_url, export_props)
        print(f"UNO_EXPORT_OK: {out_pdf} (indexes updated: {doc.getDocumentIndexes().getCount()})")
    finally:
        doc.close(True)


if __name__ == "__main__":
    main()
