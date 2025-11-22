console.log('Hello World from JavaScript!');

// 简单的交互示例
document.addEventListener('DOMContentLoaded', function() {
    console.log('页面加载完成');
    
    // 点击标题改变颜色
    const h1 = document.querySelector('h1');
    if (h1) {
        h1.addEventListener('click', function() {
            this.style.color = this.style.color === 'red' ? '#333' : 'red';
        });
    }
});