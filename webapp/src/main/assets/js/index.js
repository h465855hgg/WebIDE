console.log('Hello World from JavaScript!');

// 简单的交互示例
document.addEventListener('DOMContentLoaded', function() {
    console.log('页面加载完成');
    // 注意：context 变量已经被我们在 Binding 中注入了
    window.websApp.evalJavaCode(`
        import android.widget.Toast;
        // 需要切换到主线程显示 UI
        context.runOnUiThread(() -> {
            Toast.makeText(context, "Hello from Groovy!", Toast.LENGTH_SHORT).show();
        });
    `);
    // 点击标题改变颜色
    const h1 = document.querySelector('h1');
    if (h1) {
        h1.addEventListener('click', function() {
            this.style.color = this.style.color === 'red' ? '#333' : 'red';
        });
    }
});