<template>
  <div>
    <Member v-if="subpage == 'member'"></Member>
    <Video v-if="subpage == 'video'"></Video>
    <!-- <VideoStat v-if="subpage == 'videoStat'"></VideoStat> -->
  </div>
</template>

<script>
import Member from '../components/Member.vue';
import Video from '../components/Video.vue';
// import VideoStat from '../components/VideoStat.vue';
export default {
  components: { Member, Video },
  props:{
    subpage: String
  } 
}
</script>

