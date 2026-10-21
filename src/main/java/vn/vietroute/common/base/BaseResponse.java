package vn.vietroute.common.base;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** Response của entity kế thừa audit chung và chỉ bổ sung những trường được phép công bố. */
@Getter @Setter @SuperBuilder @NoArgsConstructor
public abstract class BaseResponse extends BaseDto { }
