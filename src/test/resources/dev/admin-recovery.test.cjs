const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
test('Auth gọn không còn form hoặc script recovery riêng',()=>{const html=fs.readFileSync('src/main/webapp/WEB-INF/views/auth/account.html','utf8');assert.equal(html.includes('admin-recovery-form'),false);assert.equal(fs.existsSync('src/main/webapp/assets/js/admin-recovery.js'),false);});
test('Trang login không liên kết tự đăng ký/reset đã cắt',()=>{const html=fs.readFileSync('src/main/webapp/WEB-INF/views/auth/login.html','utf8');assert.equal(html.includes('/auth/register'),false);assert.equal(html.includes('account-lifecycle.js'),false);});
