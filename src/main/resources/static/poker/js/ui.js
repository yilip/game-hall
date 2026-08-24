/**
 * 德州扑克 UI 渲染
 * 处理动画、特效和界面更新
 */

class PokerUI {
    constructor(game) {
        this.game = game;
        this.animations = [];
        
        this.init();
    }
    
    init() {
        // 初始化 UI 效果
        this.setupParticleEffect();
    }
    
    /**
     * 创建卡牌动画
     */
    dealCardAnimation(cardElement, fromX, fromY, toX, toY) {
        cardElement.style.position = 'absolute';
        cardElement.style.left = fromX + 'px';
        cardElement.style.top = fromY + 'px';
        cardElement.style.zIndex = '1000';
        cardElement.style.transition = 'all 0.5s ease-out';
        
        // 强制重绘
        cardElement.offsetHeight;
        
        cardElement.style.left = toX + 'px';
        cardElement.style.top = toY + 'px';
        cardElement.style.transform = 'rotate(' + (Math.random() * 10 - 5) + 'deg)';
        
        setTimeout(() => {
            cardElement.style.position = 'static';
            cardElement.style.zIndex = 'auto';
        }, 500);
    }
    
    /**
     * 筹码动画
     */
    chipAnimation(fromElement, toElement, amount) {
        const chip = document.createElement('div');
        chip.className = 'chip-animation';
        chip.innerHTML = '🪙';
        chip.style.fontSize = '24px';
        chip.style.position = 'absolute';
        chip.style.zIndex = '1000';
        
        const fromRect = fromElement.getBoundingClientRect();
        const toRect = toElement.getBoundingClientRect();
        
        chip.style.left = fromRect.left + 'px';
        chip.style.top = fromRect.top + 'px';
        
        document.body.appendChild(chip);
        
        // 动画
        chip.style.transition = 'all 0.8s ease-in-out';
        chip.style.left = toRect.left + 'px';
        chip.style.top = toRect.top + 'px';
        chip.style.opacity = '0';
        
        setTimeout(() => {
            chip.remove();
        }, 800);
    }
    
    /**
     * 获胜庆祝动画
     */
    celebrateWin(winnerElement) {
        // 创建彩带
        for (let i = 0; i < 50; i++) {
            this.createConfetti(winnerElement);
        }
        
        // 添加光晕效果
        winnerElement.style.animation = 'winnerGlow 1s ease-in-out infinite';
        
        setTimeout(() => {
            winnerElement.style.animation = '';
        }, 3000);
    }
    
    /**
     * 创建彩带
     */
    createConfetti(targetElement) {
        const confetti = document.createElement('div');
        confetti.style.position = 'fixed';
        confetti.style.width = '10px';
        confetti.style.height = '10px';
        confetti.style.backgroundColor = this.randomColor();
        confetti.style.zIndex = '9999';
        
        const targetRect = targetElement.getBoundingClientRect();
        const startX = targetRect.left + targetRect.width / 2;
        const startY = targetRect.top;
        
        confetti.style.left = startX + 'px';
        confetti.style.top = startY + 'px';
        
        document.body.appendChild(confetti);
        
        // 随机方向
        const angle = Math.random() * Math.PI * 2;
        const velocity = 5 + Math.random() * 10;
        const vx = Math.cos(angle) * velocity;
        const vy = Math.sin(angle) * velocity - 5;
        
        let x = startX;
        let y = startY;
        let opacity = 1;
        
        const animate = () => {
            x += vx;
            y += vy;
            vy += 0.3; // 重力
            opacity -= 0.02;
            
            confetti.style.left = x + 'px';
            confetti.style.top = y + 'px';
            confetti.style.opacity = opacity;
            
            if (opacity > 0) {
                requestAnimationFrame(animate);
            } else {
                confetti.remove();
            }
        };
        
        requestAnimationFrame(animate);
    }
    
    /**
     * 随机颜色
     */
    randomColor() {
        const colors = ['#e74c3c', '#f39c12', '#2ecc71', '#3498db', '#9b59b6', '#1abc9c'];
        return colors[Math.floor(Math.random() * colors.length)];
    }
    
    /**
     * 设置粒子效果背景
     */
    setupParticleEffect() {
        const canvas = document.createElement('canvas');
        canvas.id = 'particle-canvas';
        canvas.style.position = 'fixed';
        canvas.style.top = '0';
        canvas.style.left = '0';
        canvas.style.width = '100%';
        canvas.style.height = '100%';
        canvas.style.pointerEvents = 'none';
        canvas.style.zIndex = '0';
        
        document.body.insertBefore(canvas, document.body.firstChild);
        
        const ctx = canvas.getContext('2d');
        canvas.width = window.innerWidth;
        canvas.height = window.innerHeight;
        
        const particles = [];
        
        class Particle {
            constructor() {
                this.x = Math.random() * canvas.width;
                this.y = Math.random() * canvas.height;
                this.size = Math.random() * 3;
                this.speedX = Math.random() * 1 - 0.5;
                this.speedY = Math.random() * 1 - 0.5;
                this.opacity = Math.random() * 0.5 + 0.2;
            }
            
            update() {
                this.x += this.speedX;
                this.y += this.speedY;
                
                if (this.x > canvas.width) this.x = 0;
                if (this.x < 0) this.x = canvas.width;
                if (this.y > canvas.height) this.y = 0;
                if (this.y < 0) this.y = canvas.height;
            }
            
            draw() {
                ctx.fillStyle = `rgba(243, 156, 18, ${this.opacity})`;
                ctx.beginPath();
                ctx.arc(this.x, this.y, this.size, 0, Math.PI * 2);
                ctx.fill();
            }
        }
        
        // 创建粒子
        for (let i = 0; i < 100; i++) {
            particles.push(new Particle());
        }
        
        // 动画循环
        function animate() {
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            
            particles.forEach(particle => {
                particle.update();
                particle.draw();
            });
            
            requestAnimationFrame(animate);
        }
        
        animate();
        
        // 窗口大小改变时调整
        window.addEventListener('resize', () => {
            canvas.width = window.innerWidth;
            canvas.height = window.innerHeight;
        });
    }
    
    /**
     * 显示玩家操作提示
     */
    showActionHint(playerElement, action) {
        const hint = document.createElement('div');
        hint.className = 'action-hint';
        hint.textContent = this.getActionText(action);
        hint.style.position = 'absolute';
        hint.style.top = '-30px';
        hint.style.left = '50%';
        hint.style.transform = 'translateX(-50%)';
        hint.style.background = 'rgba(0, 0, 0, 0.8)';
        hint.style.color = '#fff';
        hint.style.padding = '5px 10px';
        hint.style.borderRadius = '5px';
        hint.style.fontSize = '12px';
        hint.style.whiteSpace = 'nowrap';
        hint.style.zIndex = '100';
        
        playerElement.appendChild(hint);
        
        setTimeout(() => {
            hint.remove();
        }, 1500);
    }
    
    /**
     * 获取动作文本
     */
    getActionText(action) {
        const actions = {
            'FOLD': '弃牌',
            'CHECK': '过牌',
            'CALL': '跟注',
            'RAISE': '加注',
            'ALL_IN': '全下'
        };
        return actions[action] || action;
    }
    
    /**
     * 更新底池动画
     */
    updatePotAnimation(oldPot, newPot) {
        const potElement = document.getElementById('table-pot');
        const diff = newPot - oldPot;
        
        if (diff > 0) {
            potElement.style.transform = 'scale(1.2)';
            potElement.style.transition = 'transform 0.3s ease-out';
            
            setTimeout(() => {
                potElement.style.transform = 'scale(1)';
            }, 300);
        }
    }
    
    /**
     * 显示阶段转换动画
     */
    showPhaseTransition(phase) {
        const overlay = document.createElement('div');
        overlay.className = 'phase-overlay';
        overlay.style.position = 'fixed';
        overlay.style.top = '0';
        overlay.style.left = '0';
        overlay.style.width = '100%';
        overlay.style.height = '100%';
        overlay.style.background = 'rgba(0, 0, 0, 0.8)';
        overlay.style.display = 'flex';
        overlay.style.alignItems = 'center';
        overlay.style.justifyContent = 'center';
        overlay.style.zIndex = '9999';
        
        const text = document.createElement('div');
        text.textContent = this.getPhaseName(phase);
        text.style.fontSize = '48px';
        text.style.color = '#f39c12';
        text.style.fontWeight = 'bold';
        text.style.textShadow = '0 0 20px rgba(243, 156, 18, 0.5)';
        
        overlay.appendChild(text);
        document.body.appendChild(overlay);
        
        // 淡入
        overlay.style.opacity = '0';
        overlay.style.transition = 'opacity 0.3s ease-out';
        
        setTimeout(() => {
            overlay.style.opacity = '1';
        }, 100);
        
        // 淡出
        setTimeout(() => {
            overlay.style.opacity = '0';
            setTimeout(() => {
                overlay.remove();
            }, 300);
        }, 1500);
    }
    
    /**
     * 获取阶段名称
     */
    getPhaseName(phase) {
        const phases = {
            '等待开始': '游戏即将开始',
            '翻牌前': '翻牌前',
            '翻牌': '翻牌圈',
            '转牌': '转牌圈',
            '河牌': '河牌圈',
            '摊牌': '摊牌',
            '游戏结束': '游戏结束'
        };
        return phases[phase] || phase;
    }
    
    /**
     * AI 思考动画
     */
    showAIThinking(seatIndex) {
        const seat = document.querySelector(`.seat-${seatIndex}`);
        if (seat) {
            seat.classList.add('thinking');
            
            // 显示思考气泡
            const bubble = document.createElement('div');
            bubble.className = 'thinking-bubble';
            bubble.innerHTML = '🤔';
            bubble.style.position = 'absolute';
            bubble.style.top = '-40px';
            bubble.style.left = '50%';
            bubble.style.transform = 'translateX(-50%)';
            bubble.style.fontSize = '24px';
            bubble.style.animation = 'bounce 0.5s infinite alternate';
            
            seat.querySelector('.player-card').appendChild(bubble);
            
            setTimeout(() => {
                seat.classList.remove('thinking');
                bubble.remove();
            }, 2000);
        }
    }
}

// 添加 CSS 动画
const style = document.createElement('style');
style.textContent = `
    @keyframes winnerGlow {
        0%, 100% {
            box-shadow: 0 0 20px rgba(243, 156, 18, 0.6);
        }
        50% {
            box-shadow: 0 0 40px rgba(243, 156, 18, 1);
        }
    }
    
    @keyframes bounce {
        from {
            transform: translateX(-50%) translateY(0);
        }
        to {
            transform: translateX(-50%) translateY(-10px);
        }
    }
    
    .chip-animation {
        transition: all 0.8s ease-in-out;
    }
`;
document.head.appendChild(style);

// 导出 UI 类（如果需要）
window.PokerUI = PokerUI;
