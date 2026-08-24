package com.deloitte.common.bean;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LastUpdateChangeNameDTO {

	private String ty;

	private String type;
	private LocalDate maxDate;

}
