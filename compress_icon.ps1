
Add-Type -AssemblyName System.Drawing
$image = [System.Drawing.Image]::FromFile("c:\Users\user\AndroidStudioProjects\NewDateMapDiary\app\src\main\res\drawable\kakao_app_icon_128.png")
$newImage = New-Object System.Drawing.Bitmap(128, 128)
$graphics = [System.Drawing.Graphics]::FromImage($newImage)
$graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$graphics.DrawImage($image, 0, 0, 128, 128)
$image.Dispose()
$graphics.Dispose()

$newImage.Save("c:\Users\user\AndroidStudioProjects\NewDateMapDiary\app\src\main\res\drawable\kakao_app_icon_128_final.png", [System.Drawing.Imaging.ImageFormat]::Png)
$newImage.Dispose()
