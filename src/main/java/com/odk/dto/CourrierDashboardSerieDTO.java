package com.odk.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourrierDashboardSerieDTO {
    private String periode;
    private List<CourrierDashboardBucketDTO> buckets = new ArrayList<>();
}
