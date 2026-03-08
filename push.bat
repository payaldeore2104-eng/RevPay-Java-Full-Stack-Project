@echo off
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/payaldeore2104-eng/RevPay-Java-Full-Stack-Project.git
git push -u origin main
