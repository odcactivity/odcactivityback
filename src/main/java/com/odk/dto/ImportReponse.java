/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.odk.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 *
 * @author kaloga081009
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ImportReponse {
    private int total;
    private int importes;
    private int listenoirs;
}
