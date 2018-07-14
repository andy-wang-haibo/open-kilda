//window.onload= function(){
	$(document).ready(function() {			
	var allPermissions = ['userTab','roleTab','permissionTab','addUser','editUser','deleteUser','activateUser','deActivateUser','resetPassword','addRole','addPermission'];
	var userOperations=['userTab','addUser','editUser','deleteUser','activateUser','deActivateUser'];
	var roleOperations=['roleTab','addRole','editRole','deleteRole','assignRoleToUsers','viewUsersWithRole'];
	var permissionOperations=['permissionTab','addPermission','editPermission','deletePermission','viewRolesWithPermission'];
	var commonOperations=['topology','flows','ISL','switches','userManagement','resetPassword','changePassword'];
	if(USER_SESSION != "" && USER_SESSION != undefined) {
	var userPermissions = USER_SESSION.permissions;
	var data=getPermissionsFromDOM();
	data.forEach(function(val){
		if(userPermissions.includes(val)){
			$('[permission='+val+']').removeClass('hidePermission');
			$('[permission='+val+']').addClass('showPermission');
		}	
		else
		{
			$('[permission='+val+']').removeClass('showPermission');
			$('[permission='+val+']').addClass('hidePermission');
		}
	})
   }
$('#myModal').on('hidden.bs.modal', function (e) {
	  $(this)
	    .find("input,textarea,select")
	       .val('').removeClass('errorInput')
	       .end()
	    .find("input[type=checkbox], input[type=radio]")
	       .prop("checked", "").removeClass('errorInput')
	       .end()
	  .find('.error').css("display",'none');
	})
$('#toggleMenu').click(function(e){
	$('.toggleText').toggle();
	$('#sidebar-left').toggleClass('min-sidebar')
	$('#logo_header').toggleClass('min-logo');
})
	
});
	
function getErrorFromUrl(){
		var error = '';
		var url = window.location.href;
		var urlQuerystring = url.split("?");
		 if(typeof(urlQuerystring[1])!=='undefined'){
			 var errorString =urlQuerystring[1].split("=");	
			 if(typeof(errorString[1])!='undefined'){
				 error = decodeURIComponent(errorString[1]).replace(/\+/g,' ');
			 }
		 }
		 return error;
}
function doConfirmationModal(heading, formContent, strSubmitFunc, btnText)
{	
    var html =  '<div id="modalWindow" class="modal fade in" style="display:none;">';
    html+='<div class="modal-dialog"><div class="modal-content">'
    html += '<div class="modal-header">';
    html += '<a class="close" data-dismiss="modal">×</a>';
    html += '<h4>'+heading+'</h4>'
    html += '</div>';
    html += '<div class="modal-body">';
    html += '<p>';
    html += formContent;
    html += '</div>';
    html += '<div class="modal-footer">';
    if (btnText!='') {
        html += '<span class="btn btn-success"';
        html += ' onClick="'+strSubmitFunc+'">'+btnText;
        html += '</span>';
    }
    html += '<span class="btn" data-dismiss="modal">';
    html += 'Close';
    html += '</span>'; // close button
    html += '</div>';  // footer
    html+='</div></div>';
    html += '</div>';  // modalWindow
    $("body").append(html);
    $("#modalWindow").modal()
    .on('hide.bs.modal', function () { 
    $("body").find("#modalWindow").remove(); 
    });
    
}


function hideModal()
{
    // Using a very general selector - this is because $('#modalDiv').hide
    // will remove the modal window but not the mask
    $('.modal.in').modal('hide');
}
function hasPermission(){
	//var userPermissions = USER_SESSION.permissions;
	var userPermissions = USER_SESSION.permissions;
	var data = getPermissionsFromDOM();
	data.forEach(function(val){
		if(userPermissions.includes(val)){
			$('[permission='+val+']').removeClass('hidePermission');
			$('[permission='+val+']').addClass('showPermission');
		}	
		else
		{
			$('[permission='+val+']').removeClass('showPermission');
			$('[permission='+val+']').addClass('hidePermission');
		}		
	})
}

function getPermissionsFromDOM(){
	var domPermissions=[];
	$( "[permission]" ).each(function() {
		domPermissions.push($( this ).attr("permission"));
	});
	return domPermissions;
}
//this to validation error on form
function removeCpError(elem) {
    var id = elem.name;
    if ((elem.value).trim() != '') {
        $("#" + id + "Error").hide();
        $('input[name="'+id+'"').removeClass("errorInput"); // Remove Error border
        $('textarea[name="'+id+'"').removeClass("errorInput"); // Remove Error border
    } else {
        $("#" + id + "Error").show();
        $('input[name="'+id+'"').addClass("errorInput"); // Add Error border
        $('textarea[name="'+id+'"').addClass("errorInput"); // Remove Error border
    }
}
// change password
function changePassword($event){
	$event.preventDefault(); 
	var oldPassword = $.trim(document.cpForm.oldPassword.value);
	var newPassword = $.trim(document.cpForm.newPassword.value);
	var confirmPassword = $.trim(document.cpForm.confirmPassword.value);
	var regex = /^(?=.*?[A-Z])(?=.*?[a-z])(?=.*?[0-9])(?=.*?[^\w\s]).{8,15}$/;
	var notPermittedRegex = /[%,&,+,\,",\s]/;
	$('.error').hide();
	$('input').removeClass('errorInput');
	if(oldPassword=="" || oldPassword == null){
		$('#oldPasswordError').show();
		$('input[name="oldPassword"').addClass("errorInput");
		return false;
	}
	if(newPassword=="" || newPassword == null){
		$('#newPasswordError').show();
		$('input[name="newPassword"').addClass("errorInput");
		return false;
	}
	if(confirmPassword=="" || confirmPassword == null){
		$('#confirmPasswordError').show();
		$('input[name="confirmPassword"').addClass("errorInput");
		return false;
	}
	if(newPassword !== confirmPassword){
		$('#confirmPasswordError').html('Please enter same password');
		$('#confirmPasswordError').show();
		$('input[name="confirmPassword"').addClass("errorInput");
		return false;
	}
	if(oldPassword == newPassword){
		$('#oldnewPasswordError').html('Old password & new password can not be same');
		$('#oldnewPasswordError').show();
		$('input[name="newPassword"').addClass("errorInput");
		return false;
	}
	
	if(!regex.test(newPassword)){
		$('#lengthPasswordError').html('Password should be between 8 to 15 characters which contain at least one lowercase letter, one uppercase letter, one numeric digit, and one special character.');
		$('#lengthPasswordError').show();
		$('input[name="newPassword"').addClass("errorInput");
		$('input[name="confirmPassword"').addClass("errorInput");
		return false;
	}
	if(notPermittedRegex.test(newPassword)){
		$('#lengthPasswordError').html('Password contains invalid characters. [%,&,+,\,", ] characters are not allowed');
		$('#lengthPasswordError').show();
		$('input[name="newPassword"').addClass("errorInput");
		$('input[name="confirmPassword"').addClass("errorInput");
		return false;
	}
	
	if(USER_SESSION.is2FaEnabled){
		var code = document.cpForm.code.value;
		if(code=="" || code == null){
			$('#codeError').show();
			$('input[name="code"').addClass("errorInput");
		}
		if((oldPassword=="" || oldPassword == null) ||(confirmPassword=="" || confirmPassword == null) ||(confirmPassword=="" || confirmPassword == null) || (code=="" || code == null)){
			return false;
		}
		var changePasswordData = {password:oldPassword,new_password:newPassword, code:code};
	}else{
		if((oldPassword=="" || oldPassword == null) ||(confirmPassword=="" || confirmPassword == null) ||(confirmPassword=="" || confirmPassword == null)){
			return false;
		}
		var changePasswordData = {password:oldPassword,new_password:newPassword};
	}
		$('#change_password_loader').show();
		$.ajax({url : './user/changePassword/'+USER_SESSION.userId,contentType:'application/json',dataType : "json",type : 'PUT',data: JSON.stringify(changePasswordData)}).then(function(response){
			$('#myModal').modal('hide')	
			$('#change_password_loader').hide();
			document.cpForm.oldPassword.value="";
			document.cpForm.newPassword.value="";
			document.cpForm.confirmPassword.value="";
	        common.infoMessage('Password has updated successfully.','success');
		}, function(error){
			$('#change_password_loader').hide();
			common.infoMessage(error.responseJSON['error-message'],'error');
		});
	
		
	
}

