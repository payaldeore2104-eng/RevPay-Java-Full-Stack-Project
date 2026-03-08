import os
import glob
import re

files = glob.glob(r'd:\copy\jjhoi\Revaturepay\Revpaywebapplication\sai project\RevPay\src\main\resources\templates\*.html')
count = 0
for f in files:
    with open(f, 'r', encoding='utf-8') as file:
        content = file.read()
    
    new_c = re.sub(r'<div class="brand">\s*<div class="brand-icon">💳</div>.*?\s*</div>', 
                   '<div class="brand">\n                <img th:src="@{/images/revpay-logo.png}" alt="RevPay Logo" class="app-logo" />\n            </div>', content, flags=re.DOTALL)
    
    if new_c != content:
        with open(f, 'w', encoding='utf-8') as file:
            file.write(new_c)
        count += 1
        print(f"Updated {os.path.basename(f)}")
print(f"Total updated: {count}")
