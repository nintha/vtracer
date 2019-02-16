# vtracer
B站视频追踪

=======

`vtrace-api `是后端工程，详见对应目录下的readme

`vtracer-web` 是前端工程，详见对应目录下的readme

## Install

该项目的数据库使用的是MongoDB，使用前需要导入之前的数据。

把dump文件的数据恢复到数据库中(`localhost:27017`)，在dump文件目录下执行下列命令：

```shell
mongorestore --gzip --archive=trace_member.mgz
mongorestore --gzip --archive=trace_member_info.mgz
mongorestore --gzip --archive=trace_task_item.mgz
mongorestore --gzip --archive=trace_task_result.mgz
mongorestore --gzip --archive=trace_video.mgz
mongorestore --gzip --archive=trace_video_stat.mgz
```
数据将位于`bilispider` database中.



## FAQ
        Q: 这个网站是干嘛的？
        A: 本站提供对Bilibili弹幕网中部分UP及其视频进行信息追踪功能。
        默认情况下，对UP追踪粉丝数和总播放量；对视频追踪播放量、硬币、分享数、弹幕数、收藏数、评论数、赞、踩；
        其中视频信息追踪默认持续7天（具体时间以EndTime属性为准），7天后视频的“Enable”属性会变成关闭状态，你可以手动开启让它继续追踪。
    
        Q: UP列表中的Keep On是什么？
        A: 开启Keep On后，对应UP下的所有视频会无视7天限制（即无视EndTime属性），长期追踪。
    
        Q: 怎么注册和登录？
        A: 目前不开放注册，需要账号的话可以加QQ群：722576063。
    
        Q: 网站已经半天没有新的跟踪数据了怎么办/网站好像卡住了？
        A: 由于VPS性能有限，的确会出现卡顿/追踪停止的情况，可以在群里大喊“vtracer又挂了！”。
