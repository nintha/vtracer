<template>
    <div class="date-slider"
      @mousemove="mouseMove"
      @mouseup="mouseUp"
      @mousedown="mouseDown"
      @mouseleave="mouseLeave" ref="date-slider">
        <div class="date-slider-bar" :style="{left:leftVal+'px', width:(rightVal-leftVal)+'px'}"></div>
        <div class="date-slider-center-tip">{{val2dateFormat(leftVal)}} ~ {{val2dateFormat(rightVal)}}</div>
    </div>
</template>
<script>
import moment from 'moment'
export default {
  props: {
    onchange: Function,
    startTime: Number, //timestamp
    endTime: Number
  },
  data() {
    return {
      dateSliderWidth: 0, // date-slider宽度
      leftVal: 300,
      rightVal: 700,
      prevLeftVal: 0,
      prevRightVal: 0,
      isDown: false,
      moveMode: 1, // 1-normal,2-left,3-right
      moving: false
    }
  },
  mounted() {
    this.dateSliderWidth = Number.parseInt(window.getComputedStyle(this.$refs['date-slider']).width)
    this.leftVal = 0
    this.rightVal = this.dateSliderWidth
    this.prevLeftVal = this.leftVal
    this.prevRightVal = this.rightVal
    // 修复宽度抖动
    this.$refs['date-slider'].style.width = this.dateSliderWidth + 'px'
    // console.log('dateSliderWidth', this.dateSliderWidth)
  },
  methods: {
    mouseMove(e) {
      if (this.isDown && !this.moving) {
        this.moving = true
        const inwidth = this.rightVal - this.leftVal
        switch (this.moveMode) { // 1-normal,2-left,3-right
          case 1:
            this.leftVal += e.movementX / 1.1
            this.rightVal += e.movementX / 1.1
            break
          case 2:
            this.leftVal += e.movementX / 1.1
            break
          case 3:
            this.rightVal += e.movementX / 1.1
            break
        }
        if (this.leftVal < 0) {
          this.leftVal = 0
          this.rightVal = inwidth
        }
        const outwidth = this.dateSliderWidth
        if (this.rightVal > outwidth) {
          this.rightVal = outwidth
          this.leftVal = outwidth - inwidth
        }
        const barMinWidth = 30
        if (this.leftVal + barMinWidth > this.rightVal) {
          this.moveMode == 2
            ? (this.leftVal = this.rightVal - barMinWidth)
            : (this.rightVal = this.leftVal + barMinWidth)
        }
        this.moving = false
      }
    },
    mouseDown(e) {
      this.isDown = true
      const btnwidth = 8
      console.log(e)
      if (e.target.className == 'date-slider-bar') {
        if (e.offsetX < btnwidth) {
          this.moveMode = 2
          // this.leftVal -= 1
        } else if (this.rightVal - e.offsetX - this.leftVal <= btnwidth) {
          this.moveMode = 3
          // this.rightVal += 1
        } else {
          this.moveMode = 1
        }
      }
    },
    mouseUp(e) {
      this.isDown = false
      this.handleChange()
    },
    mouseLeave(e) {
      // console.log('mouse leave')
      this.mouseUp(e)
    },
    handleChange() {
      // 执行回调
      if (this.leftVal != this.prevLeftVal || this.rightVal != this.prevRightVal) {
        this.prevLeftVal = this.leftVal
        this.prevRightVal = this.rightVal
        this.onchange &&
          this.onchange(this.val2timestamp(this.leftVal), this.val2timestamp(this.rightVal))
      }
    },
    // 滑块值转时间戳
    val2timestamp(val) {
      const rate = (this.endTime - this.startTime) / this.dateSliderWidth
      return Math.round(val * rate + this.startTime)
    },
    val2dateFormat(val) {
      return moment(this.val2timestamp(val)).format('YYYY-MM-DD HH:mm:ss')
    }
  }
}
</script>
<style scoped lang="less">
.date-slider {
  width: 95%;
  height: 40px;
  margin: auto;
  background: #cfcfcf;
  font-family: 'Lucida Sans', 'Lucida Sans Regular', 'Lucida Grande', 'Lucida Sans Unicode', Geneva,
    Verdana, sans-serif;
  font-size: 0;
  line-height: 0;
  // padding: 2px;
  position: relative;
  .date-slider-center-tip {
    position: absolute;
    right: 35%;
    bottom: 120%;
    display: block;
    // color: white;
    font-size: 15px;
  }
  .date-slider-bar {
    cursor: move;
    position: absolute;
    height: 40px;
    border-radius:6px;
    background: #2d8cf0;
    bottom: 0px;
    &::before {
      position: absolute;
      left: 0;
      bottom: 50%;
      display: block;
      color: white;
      content: '::';
      font-size: 15px;
      width: 8px;
      cursor: e-resize;
    }
    &::after {
      position: absolute;
      right: 2px;
      bottom: 50%;
      display: block;
      color: white;
      content: '::';
      font-size: 15px;
      width: 8px;
      cursor: e-resize;
    }
  }
}
</style>