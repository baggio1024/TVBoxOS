package com.github.tvbox.osc.bean;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.ArrayList;

public class AbsSortJson implements Serializable {

    @SerializedName(value = "class")
    public ArrayList<AbsJsonClass> classes;

    @SerializedName(value = "list")
    public ArrayList<AbsJson.AbsJsonVod> list;

    public AbsSortXml toAbsSortXml() {
        AbsSortXml absSortXml = new AbsSortXml();
        MovieSort movieSort = new MovieSort();
        movieSort.sortList = new ArrayList<>();
        if (classes != null) {
            for (AbsJsonClass cls : classes) {
                MovieSort.SortData sortData = new MovieSort.SortData();
                sortData.id = cls.type_id;
                sortData.name = cls.type_name;
                sortData.flag = cls.type_flag;
                
                // 修复：更稳健地解析 type_pid，防止浮点数或非数字导致解析失败
                try {
                    if (cls.type_pid != null) {
                        String pidStr = cls.type_pid.toString().replace("\"", "");
                        if (pidStr.contains(".")) {
                            // 处理类似 "1.0" 的情况
                            sortData.type_pid = (int) Double.parseDouble(pidStr);
                        } else {
                            sortData.type_pid = Integer.parseInt(pidStr);
                        }
                    } else {
                        sortData.type_pid = 0;
                    }
                } catch (Exception e) {
                    sortData.type_pid = 0; // 发生异常时默认为顶级
                }
                movieSort.sortList.add(sortData);
            }
        }
        if (list != null && !list.isEmpty()) {
            Movie movie = new Movie();
            ArrayList<Movie.Video> videos = new ArrayList<>();
            for (AbsJson.AbsJsonVod vod : list) {
                videos.add(vod.toXmlVideo());
            }
            movie.videoList = videos;
            absSortXml.list = movie;
        } else {
            absSortXml.list = null;
        }
        absSortXml.classes = movieSort;
        return absSortXml;
    }

    public static class AbsJsonClass implements Serializable {
        public String type_id;
        public String type_name;
        public String type_flag;
        public Object type_pid; // 兼容 String、Number 或 Double
    }
}
