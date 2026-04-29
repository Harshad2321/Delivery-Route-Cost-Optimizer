param($i) {
  $cities = @("Mumbai","Pune","Bangalore","Hyderabad","Chennai","Delhi","Kolkata","Ahmedabad")
  $vehicles = @("Bike","Van","Truck")
  $slots = @("Morning","Afternoon","Evening")
  $from = $cities[(Get-Random -Maximum $cities.Count)]
  $to = $cities[(Get-Random -Maximum $cities.Count)]
  $body = @{customerEmail="order$($i)@test.com";pickupCity=$from;dropCity=$to;vehicleType=$vehicles[(Get-Random -Maximum 3)];weightKg=(Get-Random -Minimum 1 -Maximum 50);pickupSlot=$slots[(Get-Random -Maximum 3)]} | ConvertTo-Json
  Invoke-RestMethod -Method Post -Uri http://localhost:8081/api/orders -ContentType application/json -Body $body -ErrorAction SilentlyContinue
}
