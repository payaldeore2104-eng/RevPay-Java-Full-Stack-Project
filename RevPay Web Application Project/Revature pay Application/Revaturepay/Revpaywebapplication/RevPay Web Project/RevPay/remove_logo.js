const fs = require('fs');
const path = require('path');

const dir = 'd:/copy/jjhoi/Revaturepay/Revpaywebapplication/sai project/RevPay/src/main/resources/templates';

function walkSync(currentDirPath, callback) {
    fs.readdirSync(currentDirPath).forEach(function (name) {
        var filePath = path.join(currentDirPath, name);
        var stat = fs.statSync(filePath);
        if (stat.isFile()) {
            callback(filePath, stat);
        } else if (stat.isDirectory()) {
            walkSync(filePath, callback);
        }
    });
}

let count = 0;
walkSync(dir, function (filePath) {
    if (filePath.endsWith('.html')) {
        let content = fs.readFileSync(filePath, 'utf8');
        let newContent = content.replace(/<img[^>]*class="app-logo"[^>]*>/g, '<div class="brand-icon">💳</div>RevPay');
        newContent = newContent.replace(/<img[^>]*class="auth-logo"[^>]*>/g, '<div style="font-size:48px;margin-bottom:16px;">💳</div>');

        if (content !== newContent) {
            fs.writeFileSync(filePath, newContent, 'utf8');
            count++;
        }
    }
});

console.log('Replaced text in ' + count + ' templates.');

const cssPath = 'd:/copy/jjhoi/Revaturepay/Revpaywebapplication/sai project/RevPay/src/main/resources/static/css/theme.css';
if (fs.existsSync(cssPath)) {
    let css = fs.readFileSync(cssPath, 'utf8');
    let originalCss = css;

    css = css.replace(/\.app-logo\s*\{[^}]*\}/g, '');
    css = css.replace(/\.auth-logo\s*\{[^}]*\}/g, '');

    if (css !== originalCss) {
        fs.writeFileSync(cssPath, css, 'utf8');
        console.log('Cleaned up theme.css');
    }
}
