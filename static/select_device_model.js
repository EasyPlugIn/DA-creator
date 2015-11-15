function select_device_model (device_model_name) {
    console.log('/da-creater/'+ device_model_name);
    document.getElementById('da-creater').src = '/da-creater/'+ device_model_name;
}
