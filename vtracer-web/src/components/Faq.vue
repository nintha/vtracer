<template>
    <div>
        <h1 style="margin-left:50px">FAQ</h1>
        <pre>
            Q: 这个网站是干嘛的？
            A: 本站提供对Bilibili弹幕网中部分UP及其视频进行信息追踪功能。
            默认情况下，对UP追踪粉丝数和总播放量以及新上传的视频；对视频追踪播放量、硬币、分享数、弹幕数、收藏数、评论数、赞、踩；
            其中视频信息追踪默认持续7天（具体时间以EndTime属性为准），7天后视频的“Enable”属性会变成关闭状态，你可以手动开启让它继续追踪。

            Q: UP列表中的Keep On是什么？
            A: 持续追踪。开启Keep On后，对应UP下的所有视频会无视7天限制（即无视EndTime属性），长期追踪。

            Q: UP列表中的Mini Mode是什么？
            A: 迷你模式。开启Mini Mode后，不再自动追踪对应UP新上传的视频。

            Q: Litte Nest是干啥的？
            A: Beta功能，请勿瞎点。

            Q: 网站已经半天没有新的跟踪数据了怎么办/网站好像卡住了？
            A: 由于VPS性能有限，的确会出现卡顿/追踪停止的情况，可以在群里大喊“vtracer又挂了！”。

            Q: 我想提Bug/建议/改进/新功能/其他疑问。
            A: Github: https://github.com/nintha/vtracer 
               E-mail: ninthakeey@hotmail.com
        </pre>

    </div>
</template>

<script>
export default {};
</script>