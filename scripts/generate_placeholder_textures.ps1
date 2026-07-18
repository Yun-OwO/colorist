# Generate simple placeholder PNG textures for Colorist.
# Each texture is a 16x16 PNG with a solid color matching the documented theme.

$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

$textureRoot = "g:\Users\Administrator\Documents\colorist\src\main\resources\assets\colorist\textures"

# (relative path, R, G, B) — colors chosen to match each item's documented theme.
$textures = @(
    @("block\magic_table_top.png",     90, 60, 130),
    @("block\magic_table_bottom.png",  60, 45, 90),
    @("block\magic_table_side.png",    75, 55, 110),
    @("block\magic_crystal_ore.png",   130, 100, 200),
    @("item\rainbow_dye.png",          240, 100, 200),
    @("item\grayscale_dye.png",        127, 127, 127),
    @("item\bleak_dye.png",            139, 126, 107),
    @("item\soil_dye.png",             139, 126, 107),
    @("item\magic_crystal.png",        170, 130, 230),
    @("item\magic_paper.png",          245, 245, 245),
    @("item\magic_book.png",           200, 100, 220)
)

foreach ($entry in $textures) {
    $path = Join-Path $textureRoot $entry[0]
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }

    $r = [int]$entry[1]; $g = [int]$entry[2]; $b = [int]$entry[3]
    $bmp = New-Object System.Drawing.Bitmap 16, 16
    $brush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, $r, $g, $b))
    $graphics = [System.Drawing.Graphics]::FromImage($bmp)
    $graphics.FillRectangle($brush, 0, 0, 16, 16)
    $graphics.Dispose()
    $brush.Dispose()
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Output "Wrote $path"
}
Write-Output "Done."
