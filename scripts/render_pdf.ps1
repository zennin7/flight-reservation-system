Add-Type -AssemblyName System.Runtime.WindowsRuntime
Add-Type -AssemblyName System.Drawing
[Windows.Storage.StorageFile, Windows.Storage, ContentType = WindowsRuntime] | Out-Null
[Windows.Data.Pdf.PdfDocument, Windows.Data.Pdf, ContentType = WindowsRuntime] | Out-Null

function Await-AsyncOp($op, $type) {
    $asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    }
    $asTaskSpecific = $asTask.MakeGenericMethod($type)
    $task = $asTaskSpecific.Invoke($null, @($op))
    $task.Wait()
    return $task.Result
}

function Await-AsyncAction($action) {
    $asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncAction'
    }
    $task = $asTask.Invoke($null, @($action))
    $task.Wait()
}

$pdfPath = (Resolve-Path "docs/boarding-pass.pdf").Path
$file = Await-AsyncOp ([Windows.Storage.StorageFile]::GetFileFromPathAsync($pdfPath)) ([Windows.Storage.StorageFile])
$pdf = Await-AsyncOp ([Windows.Data.Pdf.PdfDocument]::LoadFromFileAsync($file)) ([Windows.Data.Pdf.PdfDocument])
$page = $pdf.GetPage(0)

$stream = [Windows.Storage.Streams.InMemoryRandomAccessStream]::new()
Await-AsyncAction ($page.RenderToStreamAsync($stream))

$netStream = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($stream)
$bmp = [System.Drawing.Bitmap]::FromStream($netStream)

$outPath = (Join-Path (Resolve-Path "docs/screenshots").Path "08-boarding-pass.png")
$bmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)

$bmp.Dispose()
$netStream.Dispose()
$stream.Dispose()
$page.Dispose()

Write-Host "SUCCESS: Saved boarding pass PNG to $outPath"
