const test=require('node:test'),assert=require('node:assert/strict');
const i18n=require('../src/main/resources/static/i18n.js');
test('Arabic translates navigation, controls and dynamic scores',()=>{i18n.setLanguage('ar');assert.equal(i18n.translate('My Quizzes'),'اختباراتي السابقة');assert.equal(i18n.translate('2 of 3 correct'),'2 من 3 صحيحة');assert.notEqual(i18n.translate('YouTube connection timed out. Your video link is saved. Check your connection and retry.'),'YouTube connection timed out. Your video link is saved. Check your connection and retry.');});
test('English is restored and unsupported locales safely fall back',()=>{i18n.setLanguage('fr');assert.equal(i18n.language,'en');assert.equal(i18n.translate('My Quizzes'),'My Quizzes');});
test('unknown source content is preserved rather than fabricated',()=>{i18n.setLanguage('ar');assert.equal(i18n.translate('A triangle has three sides.'),'A triangle has three sides.');});
