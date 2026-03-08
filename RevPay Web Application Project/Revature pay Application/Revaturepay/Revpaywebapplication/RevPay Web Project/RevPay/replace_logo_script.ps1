$dir = "d:\copy\jjhoi\Revaturepay\Revpaywebapplication\sai project\RevPay\src\main\resources\templates"
$pattern = '(?s)<div class="brand">\s*<div class="brand-icon">💳</div>\s*RevPay\s*</div>'
$replacement = '<div class="brand">
                <img th:src="@{/images/revpay-logo.png}" alt="RevPay Logo" class="app-logo" />
            </div>'

$count = 0
Get-ChildItem -Path $dir -Filter *.html -Recurse | ForEach-Object {
    $c = [System.IO.File]::ReadAllText($_.FullName)
    $newC = [regex]::Replace($c, $pattern, $replacement)
    if ($c -ne $newC) {
        [System.IO.File]::WriteAllText($_.FullName, $newC, [System.Text.Encoding]::UTF8)
        $count++
    }
}
Write-Output "Updated $count files."
