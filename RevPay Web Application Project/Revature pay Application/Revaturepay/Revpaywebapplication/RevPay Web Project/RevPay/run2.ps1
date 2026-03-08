$dir = "d:\copy\jjhoi\Revaturepay\Revpaywebapplication\sai project\RevPay\src\main\resources\templates"
$pattern = '(?s)<div class="brand">\s*<div class="brand-icon">💳</div>.*?\s*</div>'
$replacement = '<div class="brand">
                <img th:src="@{/images/revpay-logo.png}" alt="RevPay Logo" class="app-logo" />
            </div>'

$count = 0
Get-ChildItem -Path $dir -Filter *.html -Recurse | ForEach-Object {
    $c = Get-Content -Path $_.FullName -Raw
    $newC = $c -replace $pattern, $replacement
    if ($c -ne $newC) {
        Set-Content -Path $_.FullName -Value $newC -Encoding UTF8
        $count++
        Write-Host "Updated $($_.Name)"
    }
}
Write-Host "Total updated: $count"
