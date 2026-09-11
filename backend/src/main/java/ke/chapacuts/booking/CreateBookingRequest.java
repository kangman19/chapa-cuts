package ke.chapacuts.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBookingRequest(
        @NotBlank(message = "Pick a cut from the list.") String serviceId,
        @NotBlank(message = "Pick a day from the list.") String date,
        @NotBlank(message = "Pick a time from the grid.") String slotTime,
        @NotBlank(message = "Tell us your name.") @Size(max = 80, message = "That name is a bit long.") String customerName,
        @NotBlank(message = "Enter a Kenyan mobile number, like 0712 345 678.") String phone,
        @Size(max = 500, message = "Keep the description under 500 characters.") String note,
        String imageId) {
}
