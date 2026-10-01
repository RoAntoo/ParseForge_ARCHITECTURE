. "$PSScriptRoot/release-common.ps1"
Add-Type -AssemblyName System.Drawing
$icons = Join-Path $script:ProjectRoot 'src/main/resources/icons'
New-Item -ItemType Directory -Path $icons -Force | Out-Null
$images = @()
foreach ($size in @(16,32,48,64,128,256)) {
    $bitmap = [Drawing.Bitmap]::new($size, $size)
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.ScaleTransform($size/256.0, $size/256.0)
    $graphics.Clear([Drawing.Color]::Transparent)
    $background = [Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml('#16283c'))
    $paper = [Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml('#eaf1f7'))
    $accent = [Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml('#ff9a4a'))
    $pen = [Drawing.Pen]::new([Drawing.ColorTranslator]::FromHtml('#16283c'), 12)
    $graphics.FillEllipse($background, 4,4,248,248)
    $graphics.FillPolygon($paper, [Drawing.Point[]]@([Drawing.Point]::new(72,40),[Drawing.Point]::new(152,40),[Drawing.Point]::new(192,80),[Drawing.Point]::new(192,186),[Drawing.Point]::new(72,186)))
    $graphics.DrawLine($pen,96,92,160,92)
    $graphics.DrawLine($pen,96,120,148,120)
    $graphics.FillPolygon($accent,[Drawing.Point[]]@([Drawing.Point]::new(122,144),[Drawing.Point]::new(204,144),[Drawing.Point]::new(182,170),[Drawing.Point]::new(166,170),[Drawing.Point]::new(166,190),[Drawing.Point]::new(194,206),[Drawing.Point]::new(108,206),[Drawing.Point]::new(138,190),[Drawing.Point]::new(138,170)))
    $stream = [IO.MemoryStream]::new()
    $bitmap.Save($stream,[Drawing.Imaging.ImageFormat]::Png)
    $images += ,$stream.ToArray()
    if ($size -eq 256) { $bitmap.Save((Join-Path $icons 'ParseForge.png'),[Drawing.Imaging.ImageFormat]::Png) }
    $graphics.Dispose(); $bitmap.Dispose(); $stream.Dispose()
    $pen.Dispose(); $background.Dispose(); $paper.Dispose(); $accent.Dispose()
}
$file = [IO.File]::Create((Join-Path $icons 'ParseForge.ico'))
$writer = [IO.BinaryWriter]::new($file)
try {
    $writer.Write([uint16]0); $writer.Write([uint16]1); $writer.Write([uint16]$images.Count)
    $offset = 6 + 16 * $images.Count
    $sizes = @(16,32,48,64,128,256)
    for($i=0;$i -lt $images.Count;$i++) {
        $dimension = if($sizes[$i] -eq 256){0}else{$sizes[$i]}
        $writer.Write([byte]$dimension);$writer.Write([byte]$dimension);$writer.Write([byte]0);$writer.Write([byte]0)
        $writer.Write([uint16]1);$writer.Write([uint16]32);$writer.Write([uint32]$images[$i].Length);$writer.Write([uint32]$offset)
        $offset += $images[$i].Length
    }
    foreach($data in $images){$writer.Write([byte[]]$data)}
} finally {$writer.Dispose();$file.Dispose()}
