$sshProcess = Start-Process -FilePath "ssh" -ArgumentList "-p", "8022", "-o", "StrictHostKeyChecking=no", "u0_a621@192.168.50.233" -NoNewWindow -PassThru -Wait:$false

Start-Sleep -Seconds 2

$wshell = New-Object -ComObject wscript.shell
$wshell.AppActivate($sshProcess.Id) | Out-Null
Start-Sleep -Milliseconds 500
$wshell.SendKeys("123")
$wshell.SendKeys("{ENTER}")

Wait-Process -Id $sshProcess.Id