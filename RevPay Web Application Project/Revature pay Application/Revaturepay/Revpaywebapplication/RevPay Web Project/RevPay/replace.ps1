$baseDir = "d:\jjhoi\Revaturepay\Revpaywebapplication\sai project\RevPay\src\main"
$files = Get-ChildItem -Path $baseDir -Recurse -File | Where-Object { $_.Extension -match "\.(html|java|sql)$" }

foreach ($file in $files) {
    try {
        $content = Get-Content -Path $file.FullName -Raw -Encoding UTF8
        $modified = $false
        
        if ($content -match "USD") {
            $content = $content -replace "USD", "INR"
            $modified = $true
        }
        
        if ($content -match "\$(?!\{|2a)") {
            $content = [regex]::Replace($content, '\$(?!\{|2a)', '₹')
            $modified = $true
        }
        
        if ($modified) {
            Set-Content -Path $file.FullName -Value $content -Encoding UTF8
            Write-Host "Updated $($file.Name)"
        }
    } catch {
        Write-Host "Error processing $($file.Name): $_"
    }
}
Write-Host "Done"
